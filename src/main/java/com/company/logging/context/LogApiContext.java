package com.company.logging.context;


public class LogApiContext implements LogContext {
    private String interfaceId;
    private String uri;
    private String method;
    private String status;
    private double elapsedMs;
    private String requestParam;
    private String requestBody;
    private String responseBody;
    private boolean isBinaryRequest;
    private boolean isBinaryResponse;
    private Exception ex;

    LogApiContext(Builder builder) {
        this.setInterfaceId(builder.interfaceId);
        this.setUri(builder.uri);
        this.setMethod(builder.method);
        this.setStatus(builder.status);
        this.setElapsedMs(builder.elapsedMs);
        this.setRequestParam(builder.requestParam);
        this.setResponseBody(builder.responseBody);
        this.setRequestBody(builder.requestBody);
        this.setBinaryRequest(builder.binaryRequest);
        this.setBinaryResponse(builder.binaryResponse);
        this.setEx(builder.exception);
    }

    public String getInterfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(String interfaceId) {
        this.interfaceId = interfaceId;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
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

    public String getRequestParam() {
        return requestParam;
    }

    public void setRequestParam(String requestParam) {
        this.requestParam = requestParam;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public boolean isBinaryRequest() {
        return isBinaryRequest;
    }

    public void setBinaryRequest(boolean binaryRequest) {
        isBinaryRequest = binaryRequest;
    }

    public boolean isBinaryResponse() {
        return isBinaryResponse;
    }

    public void setBinaryResponse(boolean binaryResponse) {
        isBinaryResponse = binaryResponse;
    }

    public Exception getEx() {
        return ex;
    }

    public void setEx(Exception ex) {
        this.ex = ex;
    }


    public static class Builder{


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
