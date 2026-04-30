package com.company.logging.core.metrics;

/**
 * MetricsCollector를 정적으로 관리하여 Spring Context 외부에서도 접근 가능하도록 하는 홀더입니다.
 * LoggingPropertiesHolder와 동일한 패턴으로 동작합니다.
 */
public final class MetricsHolder {

    private static volatile MetricsCollector collector;

    private MetricsHolder() {}

    public static void set(MetricsCollector instance) {
        MetricsHolder.collector = instance;
    }

    public static MetricsCollector get() {
        return collector;
    }

    /**
     * API 요청 지표를 기록합니다. 수집기가 없으면 무시합니다.
     */
    public static void recordApi(String method, String path, long elapsedMs, boolean isError) {
        MetricsCollector c = collector;

        if (c != null) {
            c.recordApi(method, path, elapsedMs, isError);
        }
    }

    /**
     * SQL 실행 지표를 기록합니다. 수집기가 없으면 무시합니다.
     */
    public static void recordSql(String sqlId, long elapsedMs, boolean isError, boolean isSlow) {
        MetricsCollector c = collector;

        if (c instanceof InMemoryMetricsCollector imc) {
            imc.recordSql(sqlId, elapsedMs, isError, isSlow);
        } else if (c != null) {
            c.recordSql(sqlId, elapsedMs, isError);
        }
    }
}
