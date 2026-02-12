package com.company.logging.trace;

import com.company.logging.config.LoggingProperties;
import com.company.logging.sql.SqlTrace;
import com.company.logging.sql.SqlTraceContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Iterator;
import java.util.List;
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
    public void logApi(String interfaceId, String uri, String method, String status, double elapsedMs,
                       String requestParam, String requestBody, String responseBody,
                       boolean isBinaryRequest, boolean isBinaryResponse, Exception ex) {

        String traceId = MDC.get("traceId");
        TraceLevel level = TraceContextHolder.isTrace() ? TraceLevel.TRACE : (TraceContextHolder.isDebug() ? TraceLevel.DEBUG : TraceLevel.PROD);
        boolean isError = (ex != null || hasErrorCode(responseBody));

        // 1. [API 요약] (소수점 3자리까지 ms 출력)
        logger.info("[API_PROD] trace_id={} interface_id={} uri={} method={} status={} elapsed={}ms sql_count={} sql_elapsed={}ms",
                traceId, interfaceId, uri, method, status, String.format("%.3f", elapsedMs),
                SqlTraceContextHolder.get().count(),
                SqlTraceContextHolder.get().getTotalElapsed()
        );

        // 2. [BODY]
        if (!isBinaryRequest) {
            logger.info("[REQ_BODY] trace_id={} request_param={} request_body={}", traceId, requestParam, requestBody);
            if (!isBinaryResponse && responseBody != null) {
                logger.info("[RES_BODY] trace_id={} response_body={}", traceId, responseBody);
            }
        }

        logSqlDetails(traceId, level, isError);

        // 4. [EXCEPTION]
        if (ex != null) {
            logger.info("[EXCEPTION] trace_id={} message={}", traceId, ex.getMessage(), ex);
        }
    }

    /**
     * Batch 관련 정보를 통합 로깅합니다.
     */
    public void logBatch(String jobName, String stepName, String status, double elapsedMs, Exception ex) {
        String traceId = MDC.get("traceId");
        TraceLevel level = TraceContextHolder.isTrace() ? TraceLevel.TRACE : (TraceContextHolder.isDebug() ? TraceLevel.DEBUG : TraceLevel.PROD);
        boolean isError = (ex != null);

        // 1. [BATCH 요약]
        logger.info("[BATCH_PROD] trace_id={} job_name={} step_name={} status={} elapsed={}ms sql_count={} sql_elapsed={}ms",
                traceId, jobName, stepName, status, String.format("%.3f", elapsedMs),
                SqlTraceContextHolder.get().count(),
                SqlTraceContextHolder.get().getTotalElapsed()
        );

        logSqlDetails(traceId, level, isError);

        if (ex != null) {
            logger.info("[EXCEPTION] trace_id={} message={}", traceId, ex.getMessage(), ex);
        }
    }

    private void logSqlDetails(String traceId, TraceLevel level, boolean isError) {
        long totalSqlElapsed = SqlTraceContextHolder.totalElapsed();
        int totalSlowLimit = properties.getSlow().getQuery().getTotalMs();
        int slowQueryLimit = properties.getSlow().getQuery().getMs();

        // [api 전체 쿼리 응답] 슬로우 쿼리
        if (totalSqlElapsed >= totalSlowLimit) {
            logger.info("[SQL] (TOTAL_SLOW) trace_id={} total_sql_elapsed={}ms (limit={}ms)",
                    traceId, totalSqlElapsed, totalSlowLimit);
        }

        // 3. [SQL]
        for (SqlTrace sql : SqlTraceContextHolder.getAll()) {
            boolean isSlow = sql.getElapsed() >= slowQueryLimit;
            String formattedSql = prettySqlLog(sql.getSql());

            if (isSlow) {
                logger.info("[SQL] (SLOW) trace_id={} sql_id={} elapsed={}ms query=\"{}\"",
                        traceId, sql.getSqlId(), sql.getElapsed(), formattedSql);
            } else if (level == TraceLevel.TRACE || isError) {
                logger.info("[SQL] trace_id={} sql_id={} elapsed={}ms query=\"{}\"",
                        traceId, sql.getSqlId(), sql.getElapsed(), formattedSql);
            } else if (level == TraceLevel.DEBUG) {
                logger.info("[SQL] trace_id={} sql_id={} elapsed={}ms param={}",
                        traceId, sql.getSqlId(), sql.getElapsed(), sql.getSqlParam());
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
