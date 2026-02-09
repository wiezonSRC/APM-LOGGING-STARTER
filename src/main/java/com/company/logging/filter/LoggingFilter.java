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

            if(res != null && !isBinaryResponse(res)){
                // 일반적인 텍스트/JSON 응답에 대한 로깅
                logProd(req, res, elapsed, exception);

                if(TraceContextHolder.isTrace()){
                    logTrace(req, res, elapsed, exception);
                }
                if(TraceContextHolder.isDebug()){
                    logDebug(req, res, elapsed, exception);
                }


                // 슬로우 쿼리(Slow SQL) 로깅
                SqlTraceContext ctx = SqlTraceContextHolder.get();
                if (ctx != null && ctx.getTotalElapsed() > queryTotalMs) {

                    ctx.getTraces().stream()
                            .filter(t -> t.getElapsed() > queryMs)
                            .forEach(t ->
                                    logger.warn("[SLOW_SQL] ({}) sqlId={} elapsed={}ms sqlParam=\"{}\" query=\"{}\"",
                                            MDC.get("traceId"),
                                            t.getSqlId(),
                                            t.getElapsed(),
                                            t.getSqlParam(),
                                            prettySqlLog(t.getSql()))
                            );
                }


            }else{
                // 바이너리 응답이거나 래퍼가 사용되지 않은 경우의 기본 로깅
                logger.info("[API_PROD] ({}) uri={} method={} status={} elapsed={}ms",
                        MDC.get("traceId"),
                        request.getRequestURI(),
                        request.getMethod(),
                        response.getStatus(),
                        elapsed
                );
            }

            // 컨텍스트 정리
            SqlTraceContextHolder.clear();
            TraceContextHolder.clear();
            MDC.clear();
        }
    }

    /**
     * 요청이 바이너리 데이터(예: 파일 업로드)인지 확인합니다.
     *
     * @param req 래핑된 요청 객체
     * @return 바이너리 요청이면 true
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

        // 다운로드 API가 GET이고 body가 없으며 Range 헤더가 있을 가능성 체크
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
     *
     * @param contentType Content-Type 문자열
     * @return 텍스트/JSON 계열이면 true
     */
    private boolean isTextContent(String contentType){
        return contentType != null && (
                    contentType.startsWith("application/json")
                    || contentType.contains("+json")
                );
    }

    /**
     * TRACE 레벨 로깅: 요청/응답의 헤더, 바디, 파라미터 및 실행된 SQL 전체를 상세하게 남깁니다.
     */
    private void logTrace(RequestWrapper req, ResponseWrapper res, long elapsed, Exception exception) {

        String reqContentType = req.getContentType();
        String resContentType = res.getContentType();

        boolean isTextRequest = isTextContent(reqContentType);
        boolean isTextResponse = isTextContent(resContentType);


        logger.info("[API_TRACE] ({}) uri={} method={} params=\"{}\" elapsed={}ms",
                MDC.get("traceId"),
                req.getRequestURI(),
                req.getMethod(),
                req.getParameterMap(),
                elapsed,
                exception
        );

        if (isTextRequest) {
            logger.info("[API_TRACE] [REQUEST] ({}) [IFID] {} [REQ_PARAM] param=\"{}\" [REQ_BODY] body=\"{}\"",
                    MDC.get("traceId"),
                    req.getHeader("IFID"),
                    req.getParameterMap(),
                    req.getBody());
        }


        // SQL TRACE
        for (SqlTrace sql : SqlTraceContextHolder.getAll()) {
            if (sql.getSql() != null) {
                logger.info("[API_TRACE] [SQL] ({}) sqlId={} elapsed={}ms query=\"{}\"",
                        MDC.get("traceId"),
                        sql.getSqlId(),
                        sql.getElapsed(),
                        prettySqlLog(sql.getSql()));
            }
        }


        if (isTextResponse) {
            logger.info("[API_TRACE] [RESPONSE] ({}) body=\"{}\"",
                    MDC.get("traceId"),
                    res.getBodyAsString());
        }
    }

    /**
     * 에러 발생 시 또는 에러 응답 감지 시 상세 로그를 남깁니다.
     */
    private void logError(RequestWrapper req, ResponseWrapper res, long elapsed, Exception exception) {

        String reqContentType = req.getContentType();
        String resContentType = res.getContentType();

        boolean isTextRequest = isTextContent(reqContentType);
        boolean isTextResponse = isTextContent(resContentType);


        logger.error("[ERROR] ({}) uri={} method={} params=\"{}\" elapsed={}ms",
                MDC.get("traceId"),
                req.getRequestURI(),
                req.getMethod(),
                req.getParameterMap(),
                elapsed,
                exception
        );

        if (isTextRequest) {
            logger.error("[ERROR] [REQUEST] ({}) [IFID] {} [REQ_PARAM] param=\"{}\" [REQ_BODY] body=\"{}\"",
                    MDC.get("traceId"),
                    req.getHeader("IFID"),
                    req.getParameterMap(),
                    req.getBody());
        }


        // SQL TRACE
        for (SqlTrace sql : SqlTraceContextHolder.getAll()) {
            if (sql.getSql() != null) {
                logger.error("[ERROR] [SQL] ({}) sqlId={} elapsed={}ms query=\"{}\"",
                        MDC.get("traceId"),
                        sql.getSqlId(),
                        sql.getElapsed(),
                        prettySqlLog(sql.getSql()));
            }
        }


        if (isTextResponse) {
            logger.error("[ERROR] [RESPONSE] ({}) response=\"{}\"",
                    MDC.get("traceId"),
                    res.getBodyAsString());
        }
    }

    /**
     * SQL 로그를 보기 좋게 포맷팅합니다 (불필요한 공백 제거).
     */
    private String prettySqlLog(String sql) {
        // 1. Line Comment (--) 제거
        sql = sql.replaceAll("--.*", "");

        // 2. 여러 줄 주석 (/* */) 제거
        sql = sql.replaceAll("/\\*.*\\*/", "");

        // 3. 불필요한 공백/개행 정리 (하나의 공백으로)
         sql = sql.replaceAll("\\s+", " ").trim();

        return sql;
    }

    /**
     * DEBUG 레벨 로깅: TRACE보다는 가볍지만 SQL 실행 내역 등을 포함합니다.
     */
    private void logDebug(RequestWrapper req, ResponseWrapper res, long elapsed, Exception exception) {

        String reqContentType = req.getContentType();
        String resContentType = res.getContentType();

        boolean isTextRequest = isTextContent(reqContentType);
        boolean isTextResponse = isTextContent(resContentType);

        logger.info("[API_DEBUG] ({}) uri={} method={} elapsed={}ms sqlCount={} sqlElapsed={}ms",
                MDC.get("traceId"),
                req.getRequestURI(),
                req.getMethod(),
                elapsed,
                SqlTraceContextHolder.get().count(),
                SqlTraceContextHolder.get().getTotalElapsed(),
                exception
        );

        if (isTextRequest) {
            logger.info("[API_DEBUG] [REQUEST] ({}) IFID {} [REQ_PARAM] \"{}\" [REQ_BODY] \"{}\"",
                    MDC.get("traceId"),
                    req.getHeader("IFID"),
                    req.getParameterMap(),
                    req.getBody()
            );
        }

        for (SqlTrace sql : SqlTraceContextHolder.getAll()) {
            if (sql.getSql() != null) {
                logger.info("[API_DEBUG] [SQL] ({}) sqlId={} elapsed={}ms sqlParam=\"{}\"",
                        MDC.get("traceId"),
                        sql.getSqlId(),
                        sql.getElapsed(),
                        sql.getSqlParam()
                );
            }
        }

        if (isTextResponse) {
            logger.info("[API_DEBUG] [RESPONSE] ({}) [RES_BODY] \"{}\"",
                    MDC.get("traceId"),
                    res.getBodyAsString()
            );
        }

    }

    /**
     * PROD 레벨 로깅: 가장 기본적인 요청 요약 정보만 남깁니다.
     * 예외가 발생하거나 에러 코드가 감지되면 ERROR 로그를 추가로 남깁니다.
     */
    private void logProd(RequestWrapper req, ResponseWrapper res, long elapsed, Exception exception) {
        logger.info("[API_PROD] ({}) IFID={} REQ_BODY={} uri={} method={} status={} elapsed={}ms sqlCount={} sqlElapsed={}ms",
                MDC.get("traceId"),
                req.getHeader("IFID"),
                req.getBody(),
                req.getRequestURI(),
                req.getMethod(),
                res.getStatus(),
                elapsed,
                SqlTraceContextHolder.get().count(),
                SqlTraceContextHolder.get().getTotalElapsed()
        );

        for (SqlTrace sql : SqlTraceContextHolder.getAll()) {
            if (sql != null) {
                logger.info("[API_PROD] [SQL] ({}) sqlId={} elapsed={}ms sqlParam=\"{}\"",
                        MDC.get("traceId"),
                        sql.getSqlId(),
                        sql.getElapsed(),
                        sql.getSqlParam()
                );
            }
        }

        if(exception != null || hasErrorCode(res)){
            logError(req, res, elapsed, exception);
        }
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