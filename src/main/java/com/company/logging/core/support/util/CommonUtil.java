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

    /**
     * 예외의 스택 트레이스를 Caused by를 포함하여 정해진 깊이와 라인 수만큼 추출합니다.
     * @param t 예외 객체
     * @param maxDepth Caused by를 추적할 최대 깊이
     * @param linesPerCause 각 원인별로 보여줄 최대 스택 라인 수
     * @return 포맷팅된 스택 트레이스 문자열
     */
    public static String getStackTrace(Throwable t, int maxDepth, int linesPerCause) {
        if (t == null) return null;

        StringBuilder sb = new StringBuilder();
        Throwable current = t;
        int depth = 0;

        while (current != null && depth < maxDepth) {
            if (depth > 0) {
                sb.append("\nCaused by: ");
            }

            sb.append("[").append(current.getClass().getName()).append("] ")
              .append(current.getMessage());

            StackTraceElement[] ste = current.getStackTrace();
            int count = 0;
            for (int i = 0; i < ste.length && count < linesPerCause; i++) {
                String line = ste[i].toString();
                sb.append("\n\tat ").append(line);
                count++;
            }

            if (ste.length > linesPerCause) {
                sb.append("\n\tat ... ").append(ste.length - linesPerCause).append(" more");
            }

            current = current.getCause();
            depth++;
        }

        return sb.toString();
    }
}
