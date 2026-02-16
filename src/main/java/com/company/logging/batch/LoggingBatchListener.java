package com.company.logging.batch;

import com.company.logging.context.LogBatchContext;
import com.company.logging.config.LoggingProperties;
import com.company.logging.sql.SqlTraceContextHolder;
import com.company.logging.trace.LogProcessor;
import com.company.logging.trace.TraceContextHolder;
import org.slf4j.MDC;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.lang.NonNull;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Spring Batch Job 및 Step 실행 시 로깅을 수행하는 리스너입니다.
 */
public class LoggingBatchListener implements JobExecutionListener, StepExecutionListener {

    private final LogProcessor logProcessor;
    private final LoggingProperties properties;

    public LoggingBatchListener(LoggingProperties properties) {
        this.properties = properties;
        this.logProcessor = new LogProcessor(properties);
    }

    @Override
    public void beforeJob(@NonNull JobExecution jobExecution) {
        initContext();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            double elapsedMs = 0;
            if (jobExecution.getEndTime() != null && jobExecution.getStartTime() != null) {
                elapsedMs = ChronoUnit.NANOS.between(jobExecution.getStartTime(), jobExecution.getEndTime()) / 1_000_000.0;
            }

            Exception ex = null;
            if (!jobExecution.getFailureExceptions().isEmpty()) {
                Throwable t = jobExecution.getFailureExceptions().get(0);
                if (t instanceof Exception exception) ex = exception;
                else ex = new RuntimeException(t);
            }

            LogBatchContext batchContext = new LogBatchContext.Builder()
                    .jobName(jobExecution.getJobInstance().getJobName())
                    .stepName("JOB")
                    .status(jobExecution.getStatus().toString())
                    .elapsedMs(elapsedMs)
                    .ex(ex)
                    .build();

            logProcessor.logBatch(batchContext);
        } finally {
            clearContext();
        }
    }

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        if (MDC.get("traceId") == null) {
            initContext();
        }
        SqlTraceContextHolder.init();
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        double elapsedMs = 0;
        if (stepExecution.getEndTime() != null && stepExecution.getStartTime() != null) {
            elapsedMs = ChronoUnit.NANOS.between(stepExecution.getStartTime(), stepExecution.getEndTime()) / 1_000_000.0;
        }

        Exception ex = null;
        if (!stepExecution.getFailureExceptions().isEmpty()) {
            Throwable t = stepExecution.getFailureExceptions().get(0);
            if (t instanceof Exception exception) ex = exception;
            else ex = new RuntimeException(t);
        }

        LogBatchContext batchContext = new LogBatchContext.Builder()
                .jobName(stepExecution.getJobExecution().getJobInstance().getJobName())
                .stepName(stepExecution.getStepName())
                .status(stepExecution.getStatus().toString())
                .elapsedMs(elapsedMs)
                .ex(ex)
                .build();

        logProcessor.logBatch(batchContext);
        SqlTraceContextHolder.clear();
        return null;
    }

    private void initContext() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        TraceContextHolder.init(properties.getTrace().getLevel(), false);
        SqlTraceContextHolder.init();
    }

    private void clearContext() {
        SqlTraceContextHolder.clear();
        TraceContextHolder.clear();
        MDC.clear();
    }
}
