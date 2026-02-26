package com.company.logging.core.enums;

/**
 * 로깅 레벨을 정의하는 열거형입니다.
 */
public enum TraceLevel {
    /** 상세 추적 레벨 (헤더, 바디, SQL 전체 포함) */
    TRACE,
    /** 운영 레벨 (요약 정보 위주) */
    PROD
}