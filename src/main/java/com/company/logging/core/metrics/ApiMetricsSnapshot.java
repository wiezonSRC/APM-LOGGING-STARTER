package com.company.logging.core.metrics;

/**
 * 특정 API 엔드포인트의 1분 주기 성능 지표 스냅샷입니다.
 */
public class ApiMetricsSnapshot {

    private final String key;       // "METHOD /path" 형식
    private final long count;
    private final long errorCount;
    private final long totalElapsedMs;
    private final long p50Ms;
    private final long p95Ms;
    private final long p99Ms;

    public ApiMetricsSnapshot(String key, long count, long errorCount,
                               long totalElapsedMs, long p50Ms, long p95Ms, long p99Ms) {
        this.key = key;
        this.count = count;
        this.errorCount = errorCount;
        this.totalElapsedMs = totalElapsedMs;
        this.p50Ms = p50Ms;
        this.p95Ms = p95Ms;
        this.p99Ms = p99Ms;
    }

    public String getKey() { return key; }
    public long getCount() { return count; }
    public long getErrorCount() { return errorCount; }
    public long getTotalElapsedMs() { return totalElapsedMs; }
    public long getP50Ms() { return p50Ms; }
    public long getP95Ms() { return p95Ms; }
    public long getP99Ms() { return p99Ms; }

    public double getErrorRate() {
        return count == 0 ? 0.0 : (double) errorCount / count * 100.0;
    }

    public long getAvgMs() {
        return count == 0 ? 0 : totalElapsedMs / count;
    }
}
