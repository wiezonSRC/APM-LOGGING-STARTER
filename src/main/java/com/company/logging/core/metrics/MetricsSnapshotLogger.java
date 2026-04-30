package com.company.logging.core.metrics;

import com.company.logging.core.enums.LogMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 60초 주기로 {@link MetricsCollector}의 스냅샷을 Logback으로 출력하는 스케줄러입니다.
 *
 * <p>Spring {@code @EnableScheduling} 없이 독립적인 데몬 스레드로 동작하므로,
 * 사용자 애플리케이션의 스케줄링 설정에 영향을 주지 않습니다.</p>
 *
 * <p>출력 예시:
 * <pre>
 * [METRIC_API] api="POST /api/v1/payment" count=142 errorRate=0.7% avg=87ms p50=82ms p95=234ms p99=891ms
 * [METRIC_SQL] sql_id=PaymentMapper.selectByTxId count=3241 slowCount=2 avg=3ms p50=2ms p95=18ms p99=67ms
 * </pre>
 * </p>
 */
public class MetricsSnapshotLogger implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger("Log");
    private static final int SNAPSHOT_INTERVAL_SECONDS = 60;

    private final MetricsCollector collector;
    private ScheduledExecutorService scheduler;

    public MetricsSnapshotLogger(MetricsCollector collector) {
        this.collector = collector;
    }

    @Override
    public void afterPropertiesSet() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "logging-metrics-snapshot");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleWithFixedDelay(
            this::logSnapshot,
            SNAPSHOT_INTERVAL_SECONDS,
            SNAPSHOT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void logSnapshot() {
        try {
            logApiMetrics(collector.snapshotApis());
            logSqlMetrics(collector.snapshotSqls());
        } catch (Exception ex) {
            logger.error("[METRICS_SNAPSHOT_ERROR] cause={}", ex.getMessage());
        }
    }

    private void logApiMetrics(List<ApiMetricsSnapshot> snapshots) {
        for (ApiMetricsSnapshot snap : snapshots) {
            if (snap.getCount() == 0) {
                continue;
            }

            logger.info(
                LogMarker.METRIC_API.marker(),
                "api=\"{}\" count={} errorRate={:.1f}% avg={}ms p50={}ms p95={}ms p99={}ms",
                snap.getKey(),
                snap.getCount(),
                snap.getErrorRate(),
                snap.getAvgMs(),
                snap.getP50Ms(),
                snap.getP95Ms(),
                snap.getP99Ms()
            );
        }
    }

    private void logSqlMetrics(List<SqlMetricsSnapshot> snapshots) {
        for (SqlMetricsSnapshot snap : snapshots) {
            if (snap.getCount() == 0) {
                continue;
            }

            logger.info(
                LogMarker.METRIC_SQL.marker(),
                "sql_id={} count={} slowCount={} errorCount={} avg={}ms p50={}ms p95={}ms p99={}ms",
                snap.getSqlId(),
                snap.getCount(),
                snap.getSlowCount(),
                snap.getErrorCount(),
                snap.getAvgMs(),
                snap.getP50Ms(),
                snap.getP95Ms(),
                snap.getP99Ms()
            );
        }
    }
}
