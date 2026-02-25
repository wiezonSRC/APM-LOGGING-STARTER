package com.company.logging.core.support.util;

public class CommonUtil {

    private CommonUtil(){}

    /**
     * 값을 SQL 리터럴 형식으로 변환합니다. (예: 문자열은 따옴표로 감쌈)
     */
    /**
     * 문자열이 지정된 길이를 초과할 경우 자르고 접미사를 붙입니다.
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(TRUNCATED)";
    }
}
