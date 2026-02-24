package com.company.logging.core.context;

/**
 * 로깅 컨텍스트의 공통 인터페이스입니다.
 */
public interface LogContext {
    String getTraceId();
    Exception getEx();
}
