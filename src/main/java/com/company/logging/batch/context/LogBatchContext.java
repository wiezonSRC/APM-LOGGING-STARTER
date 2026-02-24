package com.company.logging.batch.context;

import com.company.logging.core.context.LogContext;

public class LogBatchContext implements LogContext {
    private final String traceId;
    private final String jobName;
    private final String stepName;
    private final String status;
    private final double elapsedMs;
    private final Exception ex;

    LogBatchContext(Builder builder) {
        this.traceId = builder.traceId;
        this.jobName = builder.jobName;
        this.stepName = builder.stepName;
        this.status = builder.status;
        this.elapsedMs = builder.elapsedMs;
        this.ex = builder.ex;
    }

    @Override
    public String getTraceId() {
        return traceId;
    }
    public String getJobName() {
        return jobName;
    }
    public String getStepName() {
        return stepName;
    }
    public String getStatus() {
        return status;
    }
    public double getElapsedMs() {
        return elapsedMs;
    }
    public Exception getEx() {
        return ex;
    }



    public static class Builder {
        private String traceId;
        private String jobName;
        private String stepName;
        private String status;
        private double elapsedMs;
        private Exception ex;

        public Builder traceId(String traceId){
            this.traceId = traceId;
            return this;
        }
        public Builder jobName(String jobName) {
            this.jobName = jobName;
            return this;
        }

        public Builder stepName(String stepName) {
            this.stepName = stepName;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder elapsedMs(double elapsedMs) {
            this.elapsedMs = elapsedMs;
            return this;
        }

        public Builder ex(Exception ex) {
            this.ex = ex;
            return this;
        }

        public LogBatchContext build() {
            return new LogBatchContext(this);
        }
    }
}
