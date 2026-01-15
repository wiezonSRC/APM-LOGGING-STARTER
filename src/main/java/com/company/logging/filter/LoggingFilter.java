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

public class LoggingFilter extends OncePerRequestFilter {

    private final LoggingProperties properties;
    private final Logger logger = LoggerFactory.getLogger("Log");

    private static final Set<String> ERROR_CODE_KEYS = Set.of(
            "resCode",
            "res_cd",
            "code"
    );

    public LoggingFilter(LoggingProperties properties){
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        TraceLevel level=properties.getTrace().getLevel();
        int queryMs=properties.getSlow().getQuery().getMs();
        int queryTotalMs=properties.getSlow().getQuery().getTotalMs();

        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        boolean forceTrace = "true".equalsIgnoreCase(request.getHeader("X-Debug-Trace")) || "true".equalsIgnoreCase(request.getParameter("trace"));

        TraceContextHolder.init(level, forceTrace);
        SqlTraceContextHolder.init();

        RequestWrapper req = new RequestWrapper(request);
        boolean binaryRequest = isBinaryRequest(req);

        long start = System.currentTimeMillis();
        Exception exception = null;
        ResponseWrapper res = null;


        try{
            // 파일 요청일경우, response wrapper skip
            if(binaryRequest){
                res = new ResponseWrapper(response);
                filterChain.doFilter(req, response);
            }else{
                filterChain.doFilter(req, res);
            }

        }catch(Exception e){
            exception = e;
            throw e;
        }finally{

            long elapsed = System.currentTimeMillis() - start;

            if(!binaryRequest && res != null && !isBinaryResponse(res)){

                logProd(req, res, elapsed, exception);

                if(TraceContextHolder.isTrace()){
                    logTrace(req, res, elapsed, exception);
                }
                if(TraceContextHolder.isDebug()){
                    logDebug(req, res, elapsed, exception);
                }


                //SLOW 쿼리는 공통적으로 기입
                SqlTraceContext ctx = SqlTraceContextHolder.get();
                if (ctx != null && ctx.getTotalElapsed() > queryTotalMs) {

                    ctx.getTraces().stream()
                            .filter(t -> t.getElapsed() > queryMs)
                            .forEach(t ->
                                    logger.warn("[SLOW_SQL] ({}) sqlId={} elapsed={}ms sqlParam={} sql={}",
                                            MDC.get("traceId"),
                                            t.getSqlId(),
                                            t.getElapsed(),
                                            t.getSqlParam(),
                                            prettySqlLog(t.getSql()))
                            );
                }


            }else{
                logger.info("[API_PROD] ({}) uri={} method={} status={} elapsed={}ms",
                        MDC.get("traceId"),
                        request.getRequestURI(),
                        request.getMethod(),
                        response.getStatus(),
                        elapsed
                );
            }

            SqlTraceContextHolder.clear();
            TraceContextHolder.clear();
            MDC.clear();
        }
    }

    private boolean isBinaryRequest(HttpServletRequest req){
        String accept = req.getHeader("Accept");
        if(accept != null && accept.contains("application/octet-stream")) {
            return true;
        }

        String contentType = req.getContentType();
        if(contentType != null && contentType.contains("multipart/form-data")){
            return true;
        }

        // 다운로드 API가 GET이고 body 없음 + range 가능성
        String range = req.getHeader("Range");
        if (range != null) {
            return true;
        }

        return false;
    }

    private boolean isBinaryResponse(ResponseWrapper res){
        String ct = res.getContentType();
        if(ct != null && !isTextContent(ct)){
            return true;
        }

        String disposition = res.getHeader("Content-Disposition");
        if(disposition != null){
            return true;
        }

        return false;
    }

    private boolean isTextContent(String contentType){
        return contentType != null && (
                    contentType.startsWith("application/json")
                    || contentType.contains("+json")
                );
    }

    private void logTrace(RequestWrapper req, ResponseWrapper res, long elapsed, Exception exception) {

        String reqContentType = req.getContentType();
        String resContentType = res.getContentType();

        boolean isTextRequest = isTextContent(reqContentType);
        boolean isTextResponse = isTextContent(resContentType);


        logger.info("[API_TRACE] ({}) uri={} method={} params={} elapsed={}ms",
                MDC.get("traceId"),
                req.getRequestURI(),
                req.getMethod(),
                req.getParameterMap(),
                elapsed,
                exception
        );

        if (isTextRequest) {
            logger.info("[API_TRACE] [REQUEST] ({}) [IFID] {} [REQ_PARAM] {} [REQ_BODY] body={}",
                    MDC.get("traceId"),
                    req.getHeader("IFID"),
                    req.getParameterMap(),
                    req.getBody());
        }


        // SQL TRACE
        for (SqlTrace sql : SqlTraceContextHolder.getAll()) {
            if (sql.getSql() != null) {
                logger.info("[API_TRACE] [SQL] ({}) sqlId={} elapsed={}ms sql={}",
                        MDC.get("traceId"),
                        sql.getSqlId(),
                        sql.getElapsed(),
                        prettySqlLog(sql.getSql()));
            }
        }


        if (isTextResponse) {
            logger.info("[API_TRACE] [RESPONSE] ({}) body={}",
                    MDC.get("traceId"),
                    res.getBodyAsString());
        }
    }

    private void logError(RequestWrapper req, ResponseWrapper res, long elapsed, Exception exception) {

        String reqContentType = req.getContentType();
        String resContentType = res.getContentType();

        boolean isTextRequest = isTextContent(reqContentType);
        boolean isTextResponse = isTextContent(resContentType);


        logger.error("[ERROR] ({}) uri={} method={} params={} elapsed={}ms",
                MDC.get("traceId"),
                req.getRequestURI(),
                req.getMethod(),
                req.getParameterMap(),
                elapsed,
                exception
        );

        if (isTextRequest) {
            logger.error("[ERROR] [REQUEST] ({}) [IFID] {} [REQ_PARAM] {} [REQ_BODY] body={}",
                    MDC.get("traceId"),
                    req.getHeader("IFID"),
                    req.getParameterMap(),
                    req.getBody());
        }


        // SQL TRACE
        for (SqlTrace sql : SqlTraceContextHolder.getAll()) {
            if (sql.getSql() != null) {
                logger.error("[ERROR] [SQL] ({}) sqlId={} elapsed={}ms sql={}",
                        MDC.get("traceId"),
                        sql.getSqlId(),
                        sql.getElapsed(),
                        prettySqlLog(sql.getSql()));
            }
        }


        if (isTextResponse) {
            logger.error("[ERROR] [RESPONSE] ({}) body={}",
                    MDC.get("traceId"),
                    res.getBodyAsString());
        }
    }

    private String prettySqlLog(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

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
            logger.info("[API_DEBUG] [REQUEST] ({}) IFID {} [REQ_PARAM] {} [REQ_BODY] {}",
                    MDC.get("traceId"),
                    req.getHeader("IFID"),
                    req.getParameterMap(),
                    req.getBody()
            );
        }

        for (SqlTrace sql : SqlTraceContextHolder.getAll()) {
            if (sql.getSql() != null) {
                logger.info("[API_DEBUG] [SQL] ({}) sqlId={} elapsed={}ms sqlParam={}",
                        MDC.get("traceId"),
                        sql.getSqlId(),
                        sql.getElapsed(),
                        sql.getSqlParam()
                );
            }
        }

        if (isTextResponse) {
            logger.info("[API_DEBUG] [RESPONSE] ({}) [RES_BODY] {}",
                    MDC.get("traceId"),
                    res.getBodyAsString()
            );
        }

    }

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
                logger.info("[API_PROD] [SQL] ({}) sqlId={} elapsed={}ms sqlParam={}",
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
