package com.company.logging.filter;

import com.company.logging.config.LoggingProperties;
import com.company.logging.sql.SqlTrace;
import com.company.logging.sql.SqlTraceContext;
import com.company.logging.sql.SqlTraceContextHolder;
import com.company.logging.trace.TraceContextHolder;
import com.company.logging.trace.TraceLevel;
import com.company.logging.wrapper.RequestWrapper;
import com.company.logging.wrapper.ResponseWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HTTP 요청과 응답, 그리고 관련된 SQL 추적 정보를 로깅하는 필터입니다.
 * <p>
 * 요청/응답 본문을 읽기 위해 래핑(Wrapping)하고,
 * TraceContext 및 SqlTraceContext를 초기화/정리하며,
 * 설정된 TraceLevel에 따라 적절한 로그를 출력합니다.
 */
public class LoggingFilter extends OncePerRequestFilter {

    private final LoggingProperties properties;
    private final Logger logger = LoggerFactory.getLogger("Log");

    // 에러 코드로 간주할 JSON 키 목록
    private static final Set<String> ERROR_CODE_KEYS = Set.of(
            "resCode",
            "res_cd",
            "code"
    );

    public LoggingFilter(LoggingProperties properties){
        this.properties = properties;
    }

    /**
     * 필터의 핵심 로직을 수행합니다.
     * TraceContext 초기화, 요청/응답 래핑, 필터 체인 실행, 그리고 최종 로깅을 담당합니다.
     *
     * @param request 현재 HTTP 요청
     * @param response 현재 HTTP 응답
     * @param filterChain 필터 체인
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        TraceLevel level=properties.getTrace().getLevel();
        int queryMs=properties.getSlow().getQuery().getMs();
        int queryTotalMs=properties.getSlow().getQuery().getTotalMs();

        // Trace ID 생성 및 MDC 설정
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        
        // 헤더나 파라미터를 통한 강제 추적 여부 확인
        boolean forceTrace = "true".equalsIgnoreCase(request.getHeader("X-Debug-Trace")) || "true".equalsIgnoreCase(request.getParameter("trace"));

        TraceContextHolder.init(level, forceTrace);
        SqlTraceContextHolder.init();

        RequestWrapper req = new RequestWrapper(request);
        boolean binaryRequest = isBinaryRequest(req);

        long start = System.currentTimeMillis();
        Exception exception = null;
        ResponseWrapper res = null;


        try{
            // 파일 업로드 등 바이너리 요청이 아닐 경우에만 ResponseWrapper 사용 (메모리 이슈 방지)
            if(!binaryRequest){
                res = new ResponseWrapper(response);
                filterChain.doFilter(req, res);
            }else{
                filterChain.doFilter(req, response);
            }

        }catch(Exception e){
            exception = e;
            throw e;
        }finally{

            long elapsed = System.currentTimeMillis() - start;

            // 통합 로깅 호출 (PROD < DEBUG < TRACE 계층형)
            logUnified(req, res, elapsed, exception, binaryRequest);

            // 컨텍스트 정리
            SqlTraceContextHolder.clear();
            TraceContextHolder.clear();
            MDC.clear();
        }
    }

    /**
     * 통합 로깅: 계층별로 정보의 상세도를 조절하여 중복 없이 출력합니다.
     */
    private void logUnified(RequestWrapper req, ResponseWrapper res, long elapsed, Exception ex, boolean isBinaryRequest) {
        String traceId = MDC.get("traceId");
        TraceLevel level = TraceContextHolder.isTrace() ? TraceLevel.TRACE : (TraceContextHolder.isDebug() ? TraceLevel.DEBUG : TraceLevel.PROD);
        boolean isError = (ex != null || (res != null && hasErrorCode(res)));

        long totalSqlElapsed = SqlTraceContextHolder.totalElapsed();
        int totalSlowLimit = properties.getSlow().getQuery().getTotalMs();
        int slowQueryLimit = properties.getSlow().getQuery().getMs();

        // 1. [API 요약] (PROD 이상 항상 출력) - IFID 필수 포함
        logger.info("[API_PROD] trace_id={} interface_id={} uri={} method={} status={} elapsed={}ms sql_count={} sql_elapsed={}ms",
                traceId,
                req.getHeader("IFID"),
                req.getRequestURI(),
                req.getMethod(),
                res != null ? res.getStatus() : "-",
                elapsed,
                SqlTraceContextHolder.get().count(),
                SqlTraceContextHolder.get().getTotalElapsed()
        );

        // 2. [BODY] (PROD 이상 항상 출력 - 텍스트인 경우만)
        if (!isBinaryRequest) {
            logger.info("[REQ_BODY] trace_id={} request_param={} request_body={}", traceId, req.getParameterMap(), req.getBody());
            if (res != null && !isBinaryResponse(res) && isTextContent(res.getContentType())) {
                logger.info("[RES_BODY] trace_id={} response_body={}", traceId, res.getBodyAsString());
            }
        }

        // [api 전체 쿼리 응답] 슬로우 쿼리
        if(totalSqlElapsed >= totalSlowLimit){
            logger.info("[SQL] (TOTAL_SLOW) trace_id={} interface_id={} total_sql_elapsed={}ms (limit={}ms)",
                    traceId, req.getHeader("IFID"), totalSqlElapsed, totalSlowLimit);
        }

        // 3. [SQL] (레벨 및 슬로우 쿼리에 따른 상세도 조절)
        for (SqlTrace sql : SqlTraceContextHolder.getAll()) {
            boolean isSlow = sql.getElapsed() >= slowQueryLimit;
            String formattedSql = prettySqlLog(sql.getSql());

            if (isSlow) {
                // 슬로우 쿼리는 레벨 불문 출력 (중복 방지)
                logger.info("[SQL] (SLOW) trace_id={} sql_id={} elapsed={}ms query=\"{}\"",
                        traceId, sql.getSqlId(), sql.getElapsed(), formattedSql);
            } else if (level == TraceLevel.TRACE || isError) {
                // TRACE 레벨이거나 에러 발생 시 전체 쿼리 출력
                logger.info("[SQL] trace_id={} sql_id={} elapsed={}ms query=\"{}\"",
                        traceId, sql.getSqlId(), sql.getElapsed(), formattedSql);
            } else if (level == TraceLevel.DEBUG) {
                // DEBUG 레벨일 때는 파라미터까지만 출력
                logger.info("[SQL] trace_id={} sql_id={} elapsed={}ms param={}",
                        traceId, sql.getSqlId(), sql.getElapsed(), sql.getSqlParam());
            }
        }

        // 4. [EXCEPTION] 예외 발생 시 상세 스택트레이스 포함
        if (ex != null) {
            logger.info("[EXCEPTION] trace_id={} message={}", traceId, ex.getMessage(), ex);
        }
    }

    /**
     * 요청이 바이너리 데이터(예: 파일 업로드)인지 확인합니다.
     */
    private boolean isBinaryRequest(RequestWrapper req){
        String accept = req.getHeader("Accept");
        if(accept != null && accept.contains("application/octet-stream")) {
            return true;
        }

        String contentType = req.getContentType();
        if(contentType != null && contentType.contains("multipart/form-data")){
            return true;
        }

        String range = req.getHeader("Range");
        return range != null;
    }

    /**
     * 응답이 바이너리 데이터(예: 파일 다운로드)인지 확인합니다.
     *
     * @param res 래핑된 응답 객체
     * @return 바이너리 응답이면 true
     */
    private boolean isBinaryResponse(ResponseWrapper res){
        String ct = res.getContentType();
        if(ct != null && !isTextContent(ct)){
            return true;
        }

        String disposition = res.getHeader("Content-Disposition");
        return disposition != null;
    }

    /**
     * Content-Type이 텍스트(주로 JSON)인지 확인합니다.
     */
    private boolean isTextContent(String contentType){
        return contentType != null && (
                contentType.startsWith("application/json")
                        || contentType.contains("+json")
        );
    }

    /**
     * SQL 로그를 보기 좋게 포맷팅합니다.
     * Alloy 수집을 위해 한 줄로 유지하되, DBeaver 복사 시 깨지지 않도록 주석을 제거합니다.
     */
    private String prettySqlLog(String sql) {
        if (sql == null) return "";
        // 1. Line Comment (--) 제거
        sql = sql.replaceAll("--.*", "");
        // 2. 여러 줄 주석 (/* */) 제거
        sql = sql.replaceAll("/\\*.*?\\*/", "");
        // 3. 불필요한 공백/개행 정리 (하나의 공백으로)
        return sql.replaceAll("\\s+", " ").trim();
    }


    /**
     * 응답 본문에 특정 에러 코드가 포함되어 있는지 검사합니다.
     */
    private boolean hasErrorCode(ResponseWrapper res){
        String body = res.getBodyAsString();
        if(body == null || body.isEmpty()) return false;

        try{
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(body);

            return containsErrorCode(root);
        }catch(Exception e){
            return false;
        }
    }

    /**
     * JSON 노드를 재귀적으로 순회하며 에러 키와 값을 찾습니다.
     */
    private boolean containsErrorCode(JsonNode node) {

        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();

                if (ERROR_CODE_KEYS.contains(key)) {
                    if ("9999".equals(value.asText())) {
                        return true;
                    }
                }

                if (containsErrorCode(value)) {
                    return true;
                }
            }
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsErrorCode(child)) {
                    return true;
                }
            }
        }

        return false;
    }



}