package com.company.logging.core.async;

import com.company.logging.core.config.LoggingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 로그 처리 작업을 데몬 워커 스레드에 위임하는 비동기 큐입니다.
 * log.async.enabled=true 일 때 LoggingWebAutoConfiguration이 빈으로 등록합니다.
 *
 * <p>큐가 가득 찰 경우 overflowStrategy에 따라 이벤트를 DROP하거나 호출자 스레드에서 동기 처리합니다.</p>
 */
public class AsyncLogEventQueue implements InitializingBean, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger("Log");

    private final int threadCount;
    private final LoggingProperties.OverflowStrategy overflowStrategy;
    private final BlockingQueue<Runnable> queue;
    private final ExecutorService workerPool;
    private volatile boolean running = true;

    public AsyncLogEventQueue(LoggingProperties.Async asyncProps) {
        this.threadCount = asyncProps.getThreadCount();
        this.overflowStrategy = asyncProps.getOverflowStrategy();
        this.queue = new LinkedBlockingQueue<>(asyncProps.getQueueSize());
        this.workerPool = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "log-async-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void afterPropertiesSet() {
        for (int i = 0; i < threadCount; i++) {
            workerPool.submit(this::workerLoop);
        }
    }

    /**
     * 로그 처리 작업을 큐에 제출합니다.
     * 큐가 가득 찬 경우 overflowStrategy에 따라 DROP 또는 호출자 스레드에서 즉시 실행합니다.
     */
    public void offer(Runnable task) {
        if (!queue.offer(task)) {
            if (overflowStrategy == LoggingProperties.OverflowStrategy.SYNC) {
                task.run();
            } else {
                logger.warn("[LOGGING_INTERNAL_ERROR] async log queue full, event dropped");
            }
        }
    }

    private void workerLoop() {
        while (running || !queue.isEmpty()) {
            try {
                Runnable task = queue.poll(200, TimeUnit.MILLISECONDS);

                if (task != null) {
                    task.run();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("[LOGGING_INTERNAL_ERROR] async log worker error cause={}", e.getMessage());
            }
        }
    }

    @Override
    public void destroy() {
        running = false;
        workerPool.shutdown();

        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
