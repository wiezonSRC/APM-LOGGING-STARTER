package com.company.logging.filter;

import com.company.logging.config.LoggingProperties;
import com.company.logging.trace.LogProcessor;
import com.company.logging.sql.SqlTraceContextHolder;
import com.company.logging.trace.TraceContextHolder;
import com.company.logging.trace.TraceLevel;
import com.company.logging.wrapper.RequestWrapper;
import com.company.logging.wrapper.ResponseWrapper;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import com.company.logging.context.LogApiContext;

/**
 * HTTP 요청과 응답, 그리고 관련된 SQL 추적 정보를 로깅하는 필터입니다.
 * <p>
 * 요청/응답 본문을 읽기 위해 래핑(Wrapping)하고,
 * TraceContext 및 SqlTraceContext를 초기화/정리하며,
 * 설정된 TraceLevel에 따라 적절한 로그를 출력합니다.
 */
public class LoggingFilter extends OncePerRequestFilter {

    private final LoggingProperties properties;
    private final LogProcessor logProcessor;

    public LoggingFilter(LoggingProperties properties){
        this.properties = properties;
        this.logProcessor = new LogProcessor(properties);
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
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        TraceLevel level=properties.getTrace().getLevel();

        // Trace ID 생성 및 MDC 설정
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        
        // 헤더나 파라미터를 통한 강제 추적 여부 확인
        boolean forceTrace = "true".equalsIgnoreCase(request.getHeader("X-Debug-Trace")) || "true".equalsIgnoreCase(request.getParameter("trace"));

        TraceContextHolder.init(level, forceTrace);
        SqlTraceContextHolder.init();

        RequestWrapper req = new RequestWrapper(request);
        boolean binaryRequest = isBinaryRequest(req);

        long startNano = System.nanoTime();
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

            double elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;

            // 통합 로깅 호출 (PROD < DEBUG < TRACE 계층형)
            logUnified(req, res, elapsedMs, exception, binaryRequest);

            // 컨텍스트 정리
            SqlTraceContextHolder.clear();
            TraceContextHolder.clear();
            MDC.clear();
        }
    }

    /**
     * 통합 로깅: 계층별로 정보의 상세도를 조절하여 중복 없이 출력합니다.
     */
    private void logUnified(RequestWrapper req, ResponseWrapper res, double elapsedMs, Exception ex, boolean isBinaryRequest) {
        String responseBody = (res != null && !isBinaryResponse(res) && isTextContent(res.getContentType())) ? res.getBodyAsString() : null;
        boolean isBinaryRes = (res != null && isBinaryResponse(res));
        String status = (res != null) ? String.valueOf(res.getStatus()) : "-";

        LogApiContext apiContext = new LogApiContext.Builder()
                .interfaceId(req.getHeader("IFID"))
                .uri(req.getRequestURI())
                .method(req.getMethod())
                .status(status)
                .elapsedMs(elapsedMs)
                .requestParam(req.getParameterMap().toString())
                .requestBody(req.getBody())
                .responseBody(responseBody)
                .isBinaryRequest(isBinaryRequest)
                .isBinaryResponse(isBinaryRes)
                .ex(ex)
                .build();

        logProcessor.logApi(apiContext);
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






}