package com.company.logging.batch.process;

import com.company.logging.batch.context.LogBatchContext;
import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.enums.LogMarker;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.process.AbstractLogProcessor;

public class BatchLogProcessor extends AbstractLogProcessor<LogBatchContext> {
    public BatchLogProcessor(LoggingProperties properties){
        super(properties);
    }

    @Override
    public void logApi(LogBatchContext ctx) {
        String traceId = ctx.getTraceId(); // Batch는 MDC 의존하지 않는 게 더 좋음
        TraceLevel level = resolveLevel();

        boolean isError = ctx.getEx() != null;

        // 1. Batch 요약
        logger.info(
                "[{}] trace_id={} job_name={} step_name={} status={} elapsed={}ms",
                LogMarker.BATCH_PROD,
                traceId,
                ctx.getJobName(),
                ctx.getStepName(),
                ctx.getStatus(),
                String.format("%.3f", ctx.getElapsedMs())
        );

        // 2. SQL
        logSqlDetails(traceId, level, isError);

        // 3. Exception
        logException(ctx, traceId);
    }
}
