package com.company.logging.context;

public class LogNettyContext implements LogContext {
    private String interfaceId;
    private String clientIp;
    private String method;
    private String status;
    private double elapsedMs;
    private String requestData;
    private String responseData;
    private Exception ex;

    LogNettyContext(Builder builder) {
        this.setInterfaceId(builder.interfaceId);
        this.setClientIp(builder.clientIp);
        this.setMethod(builder.method);
        this.setStatus(builder.status);
        this.setElapsedMs(builder.elapsedMs);
        this.setRequestData(builder.requestData);
        this.setResponseData(builder.responseData);
        this.setEx(builder.ex);
    }

    public String getInterfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(String interfaceId) {
        this.interfaceId = interfaceId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
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

    public String getRequestData() {
        return requestData;
    }

    public void setRequestData(String requestData) {
        this.requestData = requestData;
    }

    public String getResponseData() {
        return responseData;
    }

    public void setResponseData(String responseData) {
        this.responseData = responseData;
    }

    public Exception getEx() {
        return ex;
    }

    public void setEx(Exception ex) {
        this.ex = ex;
    }

    public static class Builder {
        private String interfaceId;
        private String clientIp;
        private String method;
        private String status;
        private double elapsedMs;
        private String requestData;
        private String responseData;
        private Exception ex;

        public Builder interfaceId(String interfaceId) {
            this.interfaceId = interfaceId;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
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

        public Builder requestData(String requestData) {
            this.requestData = requestData;
            return this;
        }

        public Builder responseData(String responseData) {
            this.responseData = responseData;
            return this;
        }

        public Builder ex(Exception ex) {
            this.ex = ex;
            return this;
        }

        public LogNettyContext build() {
            return new LogNettyContext(this);
        }
    }
}
