package com.company.logging.core.support.sql;

/**
 * SQL 파싱 상태를 나타내는 열거형입니다.
 * SQL 파싱 시 문자열 리터럴이나 주석 내부를 구별하기 위해 사용합니다.
 */
public enum SqlContext {
    /** 일반 SQL 구문 */
    NORMAL,
    /** 작은 따옴표 내부 (문자열 리터럴) */
    SINGLE_QUOTE,
    /** 라인 주석 (-- ) 내부 */
    LINE_COMMENT,
    /** 블록 주석 (/* ... *\/) 내부 */
    BLOCK_COMMENT
}