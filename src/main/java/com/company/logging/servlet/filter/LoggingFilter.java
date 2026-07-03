package com.company.logging.servlet.filter;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.context.TraceContextHolder;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.support.util.SamplingDecider;
import com.company.logging.core.support.util.TraceIdUtil;
import com.company.logging.servlet.context.LogApiContext;
import com.company.logging.servlet.process.ServletLogProcessor;
import com.company.logging.servlet.wrapper.RequestWrapper;
import com.company.logging.servlet.wrapper.ResponseWrapper;
import io.micrometer.common.lang.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP 요청과 응답, 그리고 관련된 SQL 추적 정보를 로깅하는 필터입니다.
 */
public class LoggingFilter extends OncePerRequestFilter {

    private final LoggingProperties properties;
    private final ServletLogProcessor logProcessor;

    public LoggingFilter(LoggingProperties properties) {
        this.properties   = properties;
        this.logProcessor = new ServletLogProcessor(properties);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        TraceLevel level = properties.getTrace().getLevel();

        // Trace ID 및 Span ID 처리 (W3C traceparent 지원용 기반 마련)
        String traceparent = request.getHeader("traceparent");
        String traceId;
        String spanId = TraceIdUtil.generateSpanId();

        if (traceparent != null && traceparent.startsWith("00-") && traceparent.split("-").length >= 3) {
            String[] parts = traceparent.split("-");
            traceId = parts[1];
        } else {
            traceId = TraceIdUtil.generateTraceId();
        }

        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);

        // 헤더나 파라미터를 통한 강제 추적 여부 확인
        boolean forceTrace = "true".equalsIgnoreCase(request.getHeader("X-Debug-Trace")) || "true".equalsIgnoreCase(request.getParameter("trace"));

        // 샘플링 여부 결정
        forceTrace = SamplingDecider.shouldForceTrace(properties, forceTrace);

        TraceContextHolder.init(traceId, spanId, level, forceTrace);
        SqlTraceContextHolder.init();

        boolean binaryRequest = isBinaryRequest(request);
        long startNano = System.nanoTime();
        Exception exception = null;
        RequestWrapper req = null;
        ResponseWrapper res = null;

        try {
            // 파일 업로드 등 바이너리 요청이 아닐 경우에만 ResponseWrapper 사용 (메모리 이슈 방지)
            if (!binaryRequest) {
                req = new RequestWrapper(request);
                res = new ResponseWrapper(response);
                filterChain.doFilter(req, res);
            } else {
                filterChain.doFilter(request, response);
            }

        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {

            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;

            logUnified(request, req, res, elapsedMs, exception);

            // 컨텍스트 정리
            SqlTraceContextHolder.clear();
            TraceContextHolder.clear();
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }

    /**
     * 통합 로깅: 계층별로 정보의 상세도를 조절하여 중복 없이 출력합니다.
     */
    private void logUnified(HttpServletRequest originalReq, RequestWrapper wrappedReq, ResponseWrapper wrappedRes, long elapsedMs, Exception ex) {
        logProcessor.process(buildApiContext(originalReq, wrappedReq, wrappedRes, elapsedMs, ex));
    }

    private LogApiContext buildApiContext(HttpServletRequest originalReq, RequestWrapper wrappedReq,
                                          ResponseWrapper wrappedRes, long elapsedMs, Exception ex) {
        String responseBody = (wrappedRes != null && !isBinaryResponse(wrappedRes) && isTextContent(wrappedRes.getContentType())) ? wrappedRes.getBodyAsString() : "[BINARY DATA/EMPTY]";
        String status       = (wrappedRes != null) ? String.valueOf(wrappedRes.getStatus()) : "-";

        // RequestWrapper가 없으면 originalReq에서 정보를 추출 (바디는 제외)
        String uri          = (wrappedReq != null) ? wrappedReq.getRequestURI()                  : originalReq.getRequestURI();
        String method       = (wrappedReq != null) ? wrappedReq.getMethod()                      : originalReq.getMethod();
        String interfaceId  = (wrappedReq != null) ? wrappedReq.getHeader("IFID")               : originalReq.getHeader("IFID");
        String requestParam = (wrappedReq != null) ? wrappedReq.getParameterMap().toString()     : originalReq.getParameterMap().toString();
        String requestBody  = (wrappedReq != null) ? wrappedReq.getBody()                        : "[BINARY DATA/NOT WRAPPED]";

        return new LogApiContext.Builder()
                .traceId(MDC.get("traceId"))
                .spanId(MDC.get("spanId"))
                .interfaceId(interfaceId)
                .uri(uri)
                .method(method)
                .status(status)
                .elapsedMs(elapsedMs)
                .requestParam(requestParam)
                .requestBody(requestBody)
                .responseBody(responseBody)
                .ex(ex)
                .build();
    }

    /**
     * 요청이 바이너리 데이터(예: 파일 업로드)인지 확인합니다.
     */
    private boolean isBinaryRequest(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains("application/octet-stream")) {
            return true;
        }

        String contentType = req.getContentType();
        if (contentType != null && contentType.contains("multipart/form-data")) {
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
    private boolean isBinaryResponse(ResponseWrapper res) {
        String ct = res.getContentType();
        if (ct != null && !isTextContent(ct)) {
            return true;
        }

        String disposition = res.getHeader("Content-Disposition");
        return disposition != null;
    }

    /**
     * Content-Type이 텍스트(주로 JSON)인지 확인합니다.
     */
    private boolean isTextContent(String contentType) {
        return contentType != null && (
                contentType.startsWith("application/json")
                        || contentType.contains("+json")
        );
    }
}
