package com.company.logging.core.support.util;

import com.company.logging.core.enums.LogMarker;


public class LogMessageBuilder {
    private LogMessageBuilder() {}

    // 1. Exception 메시지
    public static String buildException(LogMarker marker,
                                        String traceId,
                                        String spanId,
                                        Throwable ex,
                                        int maxDepth,
                                        int maxLines) {

        String stackTrace = (ex != null) ? CommonUtil.getStackTrace(ex, maxDepth, maxLines) : "";
        return String.format("trace_id=%s span_id=%s\n%s",
                traceId,
                spanId,
                stackTrace);
    }

    // 2. TOTAL SLOW
    public static String buildTotalSlow(String traceId,
                                        String spanId,
                                        long totalElapsed,
                                        long limit) {

        return String.format(
                "(TOTAL_SLOW) trace_id=%s span_id=%s total_sql_elapsed=%dms (limit=%dms)",
                traceId,
                spanId,
                totalElapsed,
                limit
        );
    }

    // 3. SQL 로그 (쿼리와 파라미터를 한 줄에 출력)
    public static String buildSql(LogMarker marker,
                                  String traceId,
                                  String spanId,
                                  String sqlId,
                                  long elapsed,
                                  String sql,
                                  String sqlParam) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "trace_id=%s span_id=%s sql_id=%s elapsed=%dms query=\"%s\"",
                traceId,
                spanId,
                sqlId,
                elapsed,
                prettySql(sql)
        ));

        if (sqlParam != null && !sqlParam.isEmpty()) {
            sb.append(" param=").append(sqlParam);
        }

        return sb.toString();
    }

    // 5. SQL 생략 로그
    public static String buildSqlOmitted(String traceId,
                                         String spanId,
                                         int omittedCount) {

        return String.format(
                "(OMITTED) trace_id=%s span_id=%s message=\"Too many SQLs in one request. %d queries omitted.\"",
                traceId,
                spanId,
                omittedCount
        );
    }

    // SQL 정리
    private static String prettySql(String sql) {
        if (sql == null) return "";
        sql = sql.replaceAll("--.*", "");
        sql = sql.replaceAll("/\\*.*?\\*/", "");
        return sql.replaceAll("\\s+", " ").trim();
    }
}
