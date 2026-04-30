package com.company.logging.core.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * ConcurrentHashMap + LongAdder 기반의 인메모리 메트릭 수집기입니다.
 *
 * <p>스냅샷 호출 시 집계값을 초기화하므로, 1분 주기 호출을 가정한 구조입니다.
 * 스냅샷 주기 내에 최대 {@code MAX_SAMPLES_PER_KEY}개의 샘플을 보존하여 p50/p95/p99를 계산합니다.</p>
 */
public class InMemoryMetricsCollector implements MetricsCollector {

    private static final int MAX_SAMPLES_PER_KEY = 10_000;

    private final ConcurrentHashMap<String, EndpointStats> apiStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EndpointStats> sqlStats = new ConcurrentHashMap<>();

    @Override
    public void recordApi(String method, String path, long elapsedMs, boolean isError) {
        String key = method + " " + normalizePath(path);
        apiStats.computeIfAbsent(key, k -> new EndpointStats())
                .record(elapsedMs, isError, false);
    }

    @Override
    public void recordSql(String sqlId, long elapsedMs, boolean isError) {
        sqlStats.computeIfAbsent(sqlId, k -> new EndpointStats())
                .record(elapsedMs, isError, false);
    }

    public void recordSql(String sqlId, long elapsedMs, boolean isError, boolean isSlow) {
        sqlStats.computeIfAbsent(sqlId, k -> new EndpointStats())
                .record(elapsedMs, isError, isSlow);
    }

    @Override
    public List<ApiMetricsSnapshot> snapshotApis() {
        return apiStats.entrySet().stream()
                .map(entry -> entry.getValue().snapshotApi(entry.getKey()))
                .filter(snap -> snap.getCount() > 0)
                .collect(Collectors.toList());
    }

    @Override
    public List<SqlMetricsSnapshot> snapshotSqls() {
        return sqlStats.entrySet().stream()
                .map(entry -> entry.getValue().snapshotSql(entry.getKey()))
                .filter(snap -> snap.getCount() > 0)
                .collect(Collectors.toList());
    }

    // 숫자로 된 경로 변수를 {id}로 정규화하여 엔드포인트별 집계를 올바르게 유지
    private String normalizePath(String path) {
        if (path == null) {
            return "/";
        }
        return path.replaceAll("/\\d+", "/{id}");
    }

    /**
     * 단일 엔드포인트(또는 SQL ID)의 통계를 관리하는 내부 클래스입니다.
     */
    static class EndpointStats {
        private final LongAdder count = new LongAdder();
        private final LongAdder errorCount = new LongAdder();
        private final LongAdder slowCount = new LongAdder();
        private final LongAdder totalElapsed = new LongAdder();
        private final ConcurrentLinkedDeque<Long> samples = new ConcurrentLinkedDeque<>();

        void record(long elapsedMs, boolean isError, boolean isSlow) {
            count.increment();
            totalElapsed.add(elapsedMs);

            if (isError) {
                errorCount.increment();
            }

            if (isSlow) {
                slowCount.increment();
            }

            // MAX_SAMPLES 초과 시 가장 오래된 샘플부터 제거
            samples.addLast(elapsedMs);

            if (samples.size() > MAX_SAMPLES_PER_KEY) {
                samples.pollFirst();
            }
        }

        ApiMetricsSnapshot snapshotApi(String key) {
            long cnt = count.sumThenReset();
            long errCnt = errorCount.sumThenReset();
            long total = totalElapsed.sumThenReset();
            slowCount.reset();

            List<Long> drained = drainSamples();

            return new ApiMetricsSnapshot(
                key, cnt, errCnt, total,
                percentile(drained, 50),
                percentile(drained, 95),
                percentile(drained, 99)
            );
        }

        SqlMetricsSnapshot snapshotSql(String sqlId) {
            long cnt = count.sumThenReset();
            long errCnt = errorCount.sumThenReset();
            long slowCnt = slowCount.sumThenReset();
            long total = totalElapsed.sumThenReset();

            List<Long> drained = drainSamples();

            return new SqlMetricsSnapshot(
                sqlId, cnt, slowCnt, errCnt, total,
                percentile(drained, 50),
                percentile(drained, 95),
                percentile(drained, 99)
            );
        }

        private List<Long> drainSamples() {
            List<Long> result = new ArrayList<>(samples.size());
            Long s;

            while ((s = samples.poll()) != null) {
                result.add(s);
            }

            Collections.sort(result);

            return result;
        }

        private long percentile(List<Long> sorted, int pct) {
            if (sorted.isEmpty()) {
                return 0;
            }

            int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;

            return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
        }
    }
}
