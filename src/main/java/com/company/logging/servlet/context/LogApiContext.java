package com.company.logging.servlet.context;


import com.company.logging.core.context.LogContext;

public class LogApiContext implements LogContext {
    private final String traceId;
    private final String spanId;
    private final String interfaceId;
    private final String uri;
    private final String method;
    private final String status;
    private final long elapsedMs;
    private final String requestParam;
    private final String requestBody;
    private final String responseBody;
    private final Exception ex;

    LogApiContext(Builder builder) {
        this.traceId = builder.traceId;
        this.spanId = builder.spanId;
        this.interfaceId = builder.interfaceId;
        this.uri = builder.uri;
        this.method =builder.method;
        this.status =builder.status;
        this.elapsedMs = builder.elapsedMs;
        this.requestParam = builder.requestParam;
        this.responseBody = builder.responseBody;
        this.requestBody = builder.requestBody;
        this.ex = builder.exception;
    }

    @Override
    public String getTraceId() {
        return traceId;
    }

    @Override
    public String getSpanId() {
        return spanId;
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
    public long getElapsedMs() {
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



    public static class Builder{

        private String traceId;
        private String spanId;
        private String interfaceId;
        private String uri;
        private String method;
        private String status;
        private long elapsedMs;
        private String requestParam;
        private String requestBody;
        private String responseBody;
        private Exception exception;

        public Builder traceId(String traceId){
            this.traceId = traceId;
            return this;
        }
        public Builder spanId(String spanId){
            this.spanId = spanId;
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
        public Builder elapsedMs(long elapsedMs) {
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
        public Builder ex(Exception exception) {
            this.exception = exception;
            return this;
        }

        public LogApiContext build(){
            return new LogApiContext(this);
        }


    }
}
