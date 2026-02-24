package com.company.logging.tool.core.trace;

import com.company.logging.tool.core.config.LoggingProperties;
import com.company.logging.tool.batch.context.LogBatchContext;
import com.company.logging.tool.core.context.*;
import com.company.logging.tool.core.enums.LogMarker;
import com.company.logging.tool.core.sql.SqlTraceContextHolder;
import com.company.logging.tool.netty.context.LogNettyContext;
import com.company.logging.tool.servlet.context.LogApiContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 환경(Servlet, Netty, Batch 등)에 독립적인 통합 로깅 프로세서입니다.
 */
public class LogProcessor {

    private final LoggingProperties properties;
    private final Logger logger = LoggerFactory.getLogger("Log");
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 에러 코드로 간주할 JSON 키 목록
    private static final Set<String> ERROR_CODE_KEYS = Set.of(
            "resCode",
            "res_cd",
            "code"
    );

    public LogProcessor(LoggingProperties properties) {
        this.properties = properties;
    }

    /**
     * API 관련 정보를 통합 로깅합니다.
     */
    public void logApi(LogApiContext ctx) {

        String traceId = MDC.get("traceId");

        TraceLevel level;
        if (TraceContextHolder.isTrace()) {
            level = TraceLevel.TRACE;
        } else {
            if (TraceContextHolder.isDebug()) level = TraceLevel.DEBUG;
            else level = TraceLevel.PROD;
        }

        boolean isError = (ctx.getEx() != null || hasErrorCode(ctx.getResponseBody()));

        // 1. [API 요약] (소수점 3자리까지 ms 출력)
        if (logger.isInfoEnabled()) {
            logger.info("[{}] trace_id={} interface_id={} uri={} method={} status={} elapsed={}ms sql_count={} sql_elapsed={}ms",
                    LogMarker.API_PROD,
                    traceId,
                    ctx.getInterfaceId(),
                    ctx.getUri(),
                    ctx.getMethod(),
                    ctx.getStatus(),
                    String.format("%.3f", ctx.getElapsedMs()),
                    SqlTraceContextHolder.get().count(),
                    SqlTraceContextHolder.get().getTotalElapsed()
            );
        }

        // 2. [BODY]
        if (!ctx.isBinaryRequest()) {
            logger.info("[{}] trace_id={} request_param={} request_body={}",
                    LogMarker.REQ_BODY,
                    traceId,
                    ctx.getRequestParam(),
                    ctx.getRequestBody());
            if (!ctx.isBinaryResponse() && ctx.getResponseBody() != null) {
                logger.info("[{}] trace_id={} response_body={}",
                        LogMarker.RES_BODY,
                        traceId,
                        ctx.getResponseBody());
            }
        }

        logSqlDetails(traceId, level, isError);

        // 4. [EXCEPTION]
        if (ctx.getEx() != null) {
            logException(ctx, traceId);
        }
    }

    /**
     * Netty TCP 관련 정보를 통합 로깅합니다.
     */
    public void logNetty(LogNettyContext ctx) {
        String traceId = MDC.get("traceId");
        TraceLevel level;
        if (TraceContextHolder.isTrace()) {
            level = TraceLevel.TRACE;
        } else {
            if (TraceContextHolder.isDebug()) level = TraceLevel.DEBUG;
            else level = TraceLevel.PROD;
        }
        boolean isError = (ctx.getEx() != null);

        // 1. [NETTY 요약]
        if (logger.isInfoEnabled()) {
            int sqlCount = ctx.getSqlCount();
            long sqlElapsed = ctx.getSqlTotalElapsed();

            // 만약 context에 설정되지 않았다면 (ThreadLocal fallback)
            if (sqlCount == 0 && sqlElapsed == 0 && SqlTraceContextHolder.get() != null) {
                sqlCount = SqlTraceContextHolder.get().count();
                sqlElapsed = SqlTraceContextHolder.get().getTotalElapsed();
            }

            logger.info("[{}] trace_id={} interface_id={} client_ip={} method={} status={} elapsed={}ms sql_count={} sql_elapsed={}ms",
                    LogMarker.NETTY_PROD,
                    traceId,
                    ctx.getInterfaceId(),
                    ctx.getClientIp(),
                    ctx.getMethod(),
                    ctx.getStatus(),
                    String.format("%.3f", ctx.getElapsedMs()),
                    sqlCount,
                    sqlElapsed
            );
        }

        // 2. [DATA]
        logger.info("[{}] trace_id={} request_data={} response_data={}",
                LogMarker.NETTY_DATA,
                traceId,
                ctx.getRequestData(),
                ctx.getResponseData());

        logSqlDetails(traceId, level, isError);

        if (ctx.getEx() != null) {
            logException(ctx, traceId);
        }
    }

    /**
     * Batch 관련 정보를 통합 로깅합니다.
     */
    public void logBatch(LogBatchContext ctx) {
        String traceId = MDC.get("traceId");
        TraceLevel level;
        if (TraceContextHolder.isTrace()) {
            level = TraceLevel.TRACE;
        } else {
            if (TraceContextHolder.isDebug()) level = TraceLevel.DEBUG;
            else level = TraceLevel.PROD;
        }
        boolean isError = (ctx.getEx() != null);

        // 1. [BATCH 요약]
        if (logger.isInfoEnabled()) {
            logger.info("[{}] trace_id={} job_name={} step_name={} status={} elapsed={}ms sql_count={} sql_elapsed={}ms",
                    LogMarker.BATCH_PROD,
                    traceId,
                    ctx.getJobName(),
                    ctx.getStepName(),
                    ctx.getStatus(),
                    String.format("%.3f", ctx.getElapsedMs()),
                    SqlTraceContextHolder.get().count(),
                    SqlTraceContextHolder.get().getTotalElapsed()
            );
        }

        logSqlDetails(traceId, level, isError);

        if (ctx.getEx() != null) {
            logException(ctx, traceId);
        }
    }

    private void logException(LogContext ctx, String traceId) {
        logger.info("[{}] trace_id={} message={}",
                LogMarker.EXCEPTION,
                traceId,
                ctx.getEx().getMessage(),
                ctx.getEx());
    }

    private void logSqlDetails(String traceId, TraceLevel level, boolean isError) {
        long totalSqlElapsed = SqlTraceContextHolder.totalElapsed();
        int totalSlowLimit = properties.getSlow().getQuery().getTotalMs();
        int slowQueryLimit = properties.getSlow().getQuery().getMs();

        // [api 전체 쿼리 응답] 슬로우 쿼리
        if (totalSqlElapsed >= totalSlowLimit) {
            logger.info("[SQL] (TOTAL_SLOW) trace_id={} total_sql_elapsed={}ms (limit={}ms)",
                    traceId,
                    totalSqlElapsed,
                    totalSlowLimit);
        }

        // 3. [SQL]
        for (LogSqlContext sql : SqlTraceContextHolder.getAll()) {
            boolean isSlow = sql.getElapsed() >= slowQueryLimit;
            String formattedSql = prettySqlLog(sql.getSql());

            if (isSlow) {
                logger.info("[{}] trace_id={} sql_id={} elapsed={}ms query=\"{}\"",
                        LogMarker.SLOW_SQL, traceId, sql.getSqlId(), sql.getElapsed(), formattedSql);
            } else if (level == TraceLevel.TRACE || isError) {
                logger.info("[{}] trace_id={} sql_id={} elapsed={}ms query=\"{}\"",
                        LogMarker.SQL, traceId, sql.getSqlId(), sql.getElapsed(), formattedSql);
            } else if (level == TraceLevel.DEBUG) {
                logger.info("[{}] trace_id={} sql_id={} elapsed={}ms param={}",
                        LogMarker.SQL, traceId, sql.getSqlId(), sql.getElapsed(), sql.getSqlParam());
            }
        }
    }

    private String prettySqlLog(String sql) {
        if (sql == null) return "";
        sql = sql.replaceAll("--.*", "");
        sql = sql.replaceAll("/\\*.*?\\*/", "");
        return sql.replaceAll("\\s+", " ").trim();
    }

    public boolean hasErrorCode(String body) {
        if (body == null || body.isEmpty()) return false;

        try {
            JsonNode root = objectMapper.readTree(body);
            return containsErrorCode(root);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsErrorCode(JsonNode node) {
        if (node.isObject()) {
            return containsErrorInObject(node);
        }

        if (node.isArray()) {
            return containsErrorInArray(node);
        }

        return false;
    }

    private boolean containsErrorInObject(JsonNode node) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();

            if (isErrorField(entry)) {
                return true;
            }

            if (containsErrorCode(entry.getValue())) {
                return true;
            }
        }

        return false;
    }

    private boolean containsErrorInArray(JsonNode node) {
        for (JsonNode child : node) {
            if (containsErrorCode(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean isErrorField(Map.Entry<String, JsonNode> entry) {
        return ERROR_CODE_KEYS.contains(entry.getKey())
                && "9999".equals(entry.getValue().asText());
    }
}
