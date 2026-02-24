package com.company.logging.tool.batch.context;

import com.company.logging.tool.core.context.LogContext;

public class LogBatchContext implements LogContext {
    private String jobName;
    private String stepName;
    private String status;
    private double elapsedMs;
    private Exception ex;

    LogBatchContext(Builder builder) {
        this.setJobName(builder.jobName);
        this.setStepName(builder.stepName);
        this.setStatus(builder.status);
        this.setElapsedMs(builder.elapsedMs);
        this.setEx(builder.ex);
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(double elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public Exception getEx() {
        return ex;
    }

    public void setEx(Exception ex) {
        this.ex = ex;
    }

    public static class Builder {
        private String jobName;
        private String stepName;
        private String status;
        private double elapsedMs;
        private Exception ex;

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
