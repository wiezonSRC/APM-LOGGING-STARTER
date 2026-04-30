package com.company.logging.core.metrics;

/**
 * 특정 MyBatis Mapper ID의 1분 주기 SQL 성능 지표 스냅샷입니다.
 */
public class SqlMetricsSnapshot {

    private final String sqlId;
    private final long count;
    private final long slowCount;
    private final long errorCount;
    private final long totalElapsedMs;
    private final long p50Ms;
    private final long p95Ms;
    private final long p99Ms;

    public SqlMetricsSnapshot(String sqlId, long count, long slowCount, long errorCount,
                               long totalElapsedMs, long p50Ms, long p95Ms, long p99Ms) {
        this.sqlId = sqlId;
        this.count = count;
        this.slowCount = slowCount;
        this.errorCount = errorCount;
        this.totalElapsedMs = totalElapsedMs;
        this.p50Ms = p50Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
    }

    public String getSqlId() { return sqlId; }
    public long getCount() { return count; }
    public long getSlowCount() { return slowCount; }
    public long getErrorCount() { return errorCount; }
    public long getTotalElapsedMs() { return totalElapsedMs; }
    public long getP50Ms() { return p50Ms; }
    public long getP95Ms() { return p95Ms; }
    public long getP99Ms() { return p99Ms; }

    public long getAvgMs() {
        return count == 0 ? 0 : totalElapsedMs / count;
    }
}
