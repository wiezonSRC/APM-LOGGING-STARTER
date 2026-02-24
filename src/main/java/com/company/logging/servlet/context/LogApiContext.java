package com.company.logging.servlet.context;


import com.company.logging.core.context.LogContext;

public class LogApiContext implements LogContext {
    private final String traceId;
    private final String interfaceId;
    private final String uri;
    private final String method;
    private final String status;
    private final double elapsedMs;
    private final String requestParam;
    private final String requestBody;
    private final String responseBody;
    private final boolean isBinaryRequest;
    private final boolean isBinaryResponse;
    private final Exception ex;

    LogApiContext(Builder builder) {
        this.traceId = builder.traceId;
        this.interfaceId = builder.interfaceId;
        this.uri = builder.uri;
        this.method =builder.method;
        this.status =builder.status;
        this.elapsedMs = builder.elapsedMs;
        this.requestParam = builder.requestParam;
        this.responseBody = builder.responseBody;
        this.requestBody = builder.requestBody;
        this.isBinaryRequest = builder.binaryRequest;
        this.isBinaryResponse = builder.binaryResponse;
        this.ex = builder.exception;
    }

    @Override
    public String getTraceId() {
        return traceId;
    }
    public String getInterfaceId() {
        return interfaceId;
    }
    public String getUri() {
        return uri;
    }
    public String getMethod() {
        return method;
    }
    public String getStatus() {
        return status;
    }
    public double getElapsedMs() {
        return elapsedMs;
    }
    public String getResponseBody() {
        return responseBody;
    }
    public Exception getEx() {
        return ex;
    }
    public String getRequestParam() {
        return requestParam;
    }
    public String getRequestBody() {
        return requestBody;
    }
    public boolean isBinaryRequest() {
        return isBinaryRequest;
    }
    public boolean isBinaryResponse() {
        return isBinaryResponse;
    }


    public static class Builder{

        private String traceId;
        private String interfaceId;
        private String uri;
        private String method;
        private String status;
        private double elapsedMs;
        private String requestParam;
        private String requestBody;
        private String responseBody;
        private boolean binaryRequest;
        private boolean binaryResponse;
        private Exception exception;

        public Builder traceId(String traceId){
            this.traceId = traceId;
            return this;
        }
        public Builder interfaceId(String interfaceId) {
            this.interfaceId = interfaceId;
            return this;
        }
        public Builder uri(String uri) {
            this.uri = uri;
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
        public Builder requestParam(String requestParam) {
            this.requestParam = requestParam;
            return this;
        }
        public Builder requestBody(String requestBody) {
            this.requestBody = requestBody;
            return this;
        }
        public Builder responseBody(String responseBody) {
            this.responseBody = responseBody;
            return this;
        }
        public Builder isBinaryRequest(boolean binaryRequest) {
            this.binaryRequest = binaryRequest;
            return this;
        }
        public Builder isBinaryResponse(boolean binaryResponse) {
            this.binaryResponse = binaryResponse;
            return this;
        }
        public Builder ex(Exception exception) {
            this.exception = exception;
            return this;
        }

        public LogApiContext build(){
            return new LogApiContext(this);
        }


    }
}
