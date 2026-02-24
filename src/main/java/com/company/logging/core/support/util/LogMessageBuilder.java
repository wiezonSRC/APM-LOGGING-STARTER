package com.company.logging.core.support.util;

import com.company.logging.core.enums.LogMarker;


public class LogMessageBuilder {
    private LogMessageBuilder() {}

    // 1. Exception 메시지
    public static String buildException(LogMarker marker,
                                        String traceId,
                                        Throwable ex) {

        String message = (ex != null) ? ex.getMessage() : "";
        return String.format("[%s] trace_id=%s message=%s",
                marker,
                traceId,
                message);
    }

    // 2. TOTAL SLOW
    public static String buildTotalSlow(String traceId,
                                        long totalElapsed,
                                        long limit) {

        return String.format(
                "[SQL] (TOTAL_SLOW) trace_id=%s total_sql_elapsed=%dms (limit=%dms)",
                traceId,
                totalElapsed,
                limit
        );
    }

    // 3. SQL 로그
    public static String buildSql(LogMarker marker,
                                  String traceId,
                                  String sqlId,
                                  long elapsed,
                                  String sql) {

        return String.format(
                "[%s] trace_id=%s sql_id=%s elapsed=%dms query=\"%s\"",
                marker,
                traceId,
                sqlId,
                elapsed,
                prettySql(sql)
        );
    }

    // 4. SQL Param 로그
    public static String buildSqlParam(LogMarker marker,
                                       String traceId,
                                       String sqlId,
                                       long elapsed,
                                       Object param) {

        return String.format(
                "[%s] trace_id=%s sql_id=%s elapsed=%dms param=%s",
                marker,
                traceId,
                sqlId,
                elapsed,
                param
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
