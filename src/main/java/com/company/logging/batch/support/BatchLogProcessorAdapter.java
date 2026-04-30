package com.company.logging.batch.support;

import com.company.logging.batch.process.BatchLogProcessor;
import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.enums.TraceLevel;

/**
 * BatchLogProcessor의 protected logSqlDetails()를 태스크 데코레이터 등 외부에서 안전하게
 * 호출하기 위한 어댑터입니다. 실패가 태스크 실행 결과에 전파되지 않도록 자체 fail-safe를 포함합니다.
 */
public class BatchLogProcessorAdapter extends BatchLogProcessor {

    public BatchLogProcessorAdapter(LoggingProperties properties) {
        super(properties);
    }

    public void logSqlOnly(String traceId, String spanId, TraceLevel level) {
        try {
            logSqlDetails(traceId, spanId, level, false);
        } catch (Exception ex) {
            logger.error("[LOGGING_INTERNAL_ERROR] task SQL logging failed traceId={} cause={}", traceId, ex.getMessage());
        }
    }
}
