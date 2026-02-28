package com.company.logging.batch.listener;

import com.company.logging.batch.context.LogBatchContext;
import com.company.logging.batch.process.BatchLogProcessor;
import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.context.TraceContextHolder;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.lang.NonNull;

import java.time.temporal.ChronoUnit;
import com.company.logging.core.support.util.TraceIdUtil;

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
        String traceId = TraceIdUtil.generateTraceId();
        String jobSpanId = TraceIdUtil.generateSpanId();

        // JobExecutionContext에 저장 (Step들이 공유 가능)
        jobExecution.getExecutionContext().put("traceId", traceId);
        jobExecution.getExecutionContext().put("jobSpanId", jobSpanId);
        jobExecution.getExecutionContext().put("jobStartNano", System.nanoTime());

        TraceContextHolder.init(traceId, jobSpanId, properties.getTrace().getLevel(), false);
        SqlTraceContextHolder.init();
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        try {
            String traceId = jobExecution.getExecutionContext().getString("traceId");
            String jobSpanId = jobExecution.getExecutionContext().getString("jobSpanId");
            Long startNano = (Long) jobExecution.getExecutionContext().get("jobStartNano");

            double elapsedMs = 0;
            if (startNano != null) {
                elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;
            }

            Exception ex = null;
            if (!jobExecution.getFailureExceptions().isEmpty()) {
                Throwable t = jobExecution.getFailureExceptions().get(0);
                if (t instanceof Exception exception) ex = exception;
                else ex = new RuntimeException(t);
            }

            LogBatchContext batchContext = new LogBatchContext.Builder()
                    .traceId(traceId)
                    .spanId(jobSpanId)
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
        String traceId = stepExecution.getJobExecution().getExecutionContext().getString("traceId");
        String stepSpanId = TraceIdUtil.generateSpanId();

        // Step ExecutionContext에 개별 Span ID 저장
        stepExecution.getExecutionContext().put("stepSpanId", stepSpanId);
        stepExecution.getExecutionContext().put("stepStartNano", System.nanoTime());

        // 해당 Step 내에서 실행될 SQL 추적을 위해 컨텍스트 업데이트
        TraceContextHolder.init(traceId, stepSpanId, properties.getTrace().getLevel(), false);
        SqlTraceContextHolder.init();
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {

        String traceId = stepExecution.getJobExecution().getExecutionContext().getString("traceId");
        String stepSpanId = stepExecution.getExecutionContext().getString("stepSpanId");
        Long startNano = (Long) stepExecution.getExecutionContext().get("stepStartNano");

        double elapsedMs = 0;
        if (startNano != null) {
            elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;
        }

        Exception ex = null;
        if (!stepExecution.getFailureExceptions().isEmpty()) {
            Throwable t = stepExecution.getFailureExceptions().get(0);
            if (t instanceof Exception exception) ex = exception;
            else ex = new RuntimeException(t);
        }

        LogBatchContext batchContext = new LogBatchContext.Builder()
                .traceId(traceId)
                .spanId(stepSpanId)
                .jobName(stepExecution.getJobExecution().getJobInstance().getJobName())
                .stepName(stepExecution.getStepName())
                .status(stepExecution.getStatus().toString())
                .elapsedMs(elapsedMs)
                .ex(ex)
                .build();

        logProcessor.logApi(batchContext);

        // Step 종료 후 SQL 컨텍스트 정리 (Job 레벨 컨텍스트 복원 대신 각 Step 독립적 관리)
        SqlTraceContextHolder.clear();

        return stepExecution.getExitStatus();
    }


    private void clearContext() {
        SqlTraceContextHolder.clear();
        TraceContextHolder.clear();
    }
}
