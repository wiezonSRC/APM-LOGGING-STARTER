package com.company.logging.batch.support;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.config.LoggingPropertiesHolder;
import com.company.logging.core.context.TraceContextHolder;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.support.util.TraceIdUtil;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * 부모 스레드의 MDC, TraceContext(traceId 등)를 자식 스레드로 복사하고,
 * 자식 스레드만의 독립적인 spanId를 생성하여 SQL 정보를 수집/로깅하기 위한 데코레이터.
 */
public class LoggingTaskDecorator implements TaskDecorator {

    private volatile BatchLogProcessorAdapter adapter;

    @Override
    public Runnable decorate(Runnable runnable) {
        // 1. 부모 스레드의 컨텍스트 정보를 미리 복사
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        String traceId = TraceContextHolder.traceId();
        TraceLevel level = TraceContextHolder.level();
        boolean forceTrace = TraceContextHolder.isForceTrace();

        return () -> {
            // 2. 자식 스레드(Worker) 전용 새로운 spanId 생성
            String childSpanId = TraceIdUtil.generateSpanId();

            try {
                // 3. 자식 스레드에 MDC 설정 및 새로운 spanId 반영
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                MDC.put("spanId", childSpanId);

                // 4. TraceContextHolder 초기화 (부모 traceId + 자식 spanId)
                TraceContextHolder.init(traceId, childSpanId, level, forceTrace);
                
                // 5. SQL 추적 컨텍스트 초기화 (자식 스레드별로 독립적으로 수집)
                SqlTraceContextHolder.init();

                runnable.run();

                // 6. 태스크 종료 전 수집된 SQL 로그 출력
                logTaskSql(traceId, childSpanId, level);

            } finally {
                // 7. 컨텍스트 정리
                SqlTraceContextHolder.clear();
                TraceContextHolder.clear();
                MDC.clear();
            }
        };
    }

    /**
     * 자식 스레드에서 수집된 SQL 정보들을 로그로 출력합니다.
     */
    private void logTaskSql(String traceId, String spanId, TraceLevel level) {
        LoggingProperties props = LoggingPropertiesHolder.getProperties();
        if (props == null) {
            return;
        }

        getAdapter(props).logSqlOnly(traceId, spanId, level);
    }

    private BatchLogProcessorAdapter getAdapter(LoggingProperties props) {
        if (adapter == null) {
            synchronized (this) {
                if (adapter == null) {
                    adapter = new BatchLogProcessorAdapter(props);
                }
            }
        }

        return adapter;
    }
}