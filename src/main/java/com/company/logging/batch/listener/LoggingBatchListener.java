package com.company.logging.batch.listener;

import com.company.logging.batch.context.LogBatchContext;
import com.company.logging.batch.process.BatchLogProcessor;
import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.context.TraceContextHolder;
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

    private final BatchLogProcessor logProcessor;
    private final LoggingProperties properties;

    public LoggingBatchListener(LoggingProperties properties) {
        this.properties = properties;
        this.logProcessor = new BatchLogProcessor(properties);
    }

    @Override
    public void beforeJob(@NonNull JobExecution jobExecution) {
        String traceId = UUID.randomUUID().toString();

        // JobExecutionContext에 저장
        jobExecution.getExecutionContext().put("traceId", traceId);

        TraceContextHolder.init(properties.getTrace().getLevel(), false);
        SqlTraceContextHolder.init();

    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            String traceId =
                    jobExecution.getExecutionContext().getString("traceId");

            double elapsedMs = 0;
            if (jobExecution.getEndTime() != null && jobExecution.getStartTime() != null) {
                elapsedMs = ChronoUnit.MILLIS.between(
                        jobExecution.getStartTime(),
                        jobExecution.getEndTime()
                );
            }

            Exception ex = null;
            if (!jobExecution.getFailureExceptions().isEmpty()) {
                Throwable t = jobExecution.getFailureExceptions().get(0);
                if (t instanceof Exception exception) ex = exception;
                else ex = new RuntimeException(t);
            }

            LogBatchContext batchContext = new LogBatchContext.Builder()
                    .traceId(traceId)
                    .jobName(jobExecution.getJobInstance().getJobName())
                    .stepName("JOB")
                    .status(jobExecution.getStatus().toString())
                    .elapsedMs(elapsedMs)
                    .ex(ex)
                    .build();

            logProcessor.logApi(batchContext);
        } finally {
            clearContext();
        }
    }

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        String traceId = stepExecution.getJobExecution()
                            .getExecutionContext()
                            .getString("traceId");

        SqlTraceContextHolder.init();
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {

        String traceId =
                stepExecution.getJobExecution()
                        .getExecutionContext()
                        .getString("traceId");

        double elapsedMs = 0;
        if (stepExecution.getEndTime() != null && stepExecution.getStartTime() != null) {
            elapsedMs = ChronoUnit.MILLIS.between(
                    stepExecution.getStartTime(),
                    stepExecution.getEndTime()
            );
        }

        Exception ex = null;
        if (!stepExecution.getFailureExceptions().isEmpty()) {
            Throwable t = stepExecution.getFailureExceptions().get(0);
            if (t instanceof Exception exception) ex = exception;
            else ex = new RuntimeException(t);
        }

        LogBatchContext batchContext = new LogBatchContext.Builder()
                .traceId(traceId)
                .jobName(stepExecution.getJobExecution().getJobInstance().getJobName())
                .stepName(stepExecution.getStepName())
                .status(stepExecution.getStatus().toString())
                .elapsedMs(elapsedMs)
                .ex(ex)
                .build();

        logProcessor.logApi(batchContext);
        SqlTraceContextHolder.clear();

        return stepExecution.getExitStatus();
    }


    private void clearContext() {
        SqlTraceContextHolder.clear();
        TraceContextHolder.clear();
    }
}
