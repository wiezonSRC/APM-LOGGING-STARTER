package com.company.logging.core.error;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 예외로부터 버그 식별용 지문(Fingerprint)을 생성하는 유틸리티 클래스입니다.
 *
 * <p>동일한 예외가 반복 발생할 경우 동일한 fingerprint를 반환하므로,
 * Grafana에서 errorFingerprint 필드를 기준으로 집계하면 같은 버그를 그룹핑할 수 있습니다.</p>
 *
 * <p>지문 생성 기준 (순서대로):
 * <ol>
 *   <li>최상위 예외 클래스명</li>
 *   <li>스택 트레이스 중 첫 번째 자사 패키지(com.company.*) 프레임</li>
 *   <li>Root Cause 예외 클래스명</li>
 * </ol>
 * 위 세 값을 ":" 로 결합한 후 SHA-256 해시의 앞 12자리(6바이트)를 반환합니다.
 * 충돌 확률: 1 / 2^48 — 실용적으로 충분합니다.</p>
 */
public final class ErrorFingerprinter {

    private static final String COMPANY_PACKAGE_PREFIX = "com.company.";
    private static final int MAX_CAUSE_DEPTH = 5;

    private ErrorFingerprinter() {}

    /**
     * 주어진 예외로부터 12자리 16진수 지문을 생성합니다.
     *
     * @param ex 지문을 생성할 예외
     * @return 12자리 16진수 문자열 (예: "a3f9b2c11d04"), 예외가 null이면 "unknown"
     */
    public static String fingerprint(Throwable ex) {
        if (ex == null) {
            return "unknown";
        }

        String key = buildFingerprintKey(ex);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(12);

            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i]));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // JVM 필수 알고리즘이므로 사실상 도달 불가한 경로
            return Integer.toHexString(key.hashCode());
        }
    }

    private static String buildFingerprintKey(Throwable ex) {
        StringBuilder key = new StringBuilder();

        // 1. 최상위 예외 클래스명
        key.append(ex.getClass().getName()).append(":");

        // 2. 첫 번째 자사 패키지 스택 프레임 (외부 프레임 노이즈 제거)
        String firstFrame = findFirstCompanyFrame(ex);
        key.append(firstFrame).append(":");

        // 3. Root Cause 클래스명
        key.append(findRootCause(ex).getClass().getName());

        return key.toString();
    }

    private static String findFirstCompanyFrame(Throwable ex) {
        for (StackTraceElement frame : ex.getStackTrace()) {
            if (frame.getClassName().startsWith(COMPANY_PACKAGE_PREFIX)) {
                return frame.getClassName() + "." + frame.getMethodName();
            }
        }

        // 자사 프레임이 없으면 첫 번째 프레임 사용
        StackTraceElement[] stack = ex.getStackTrace();

        return (stack.length > 0) ? stack[0].getClassName() + "." + stack[0].getMethodName() : "unknown";
    }

    private static Throwable findRootCause(Throwable ex) {
        Throwable current = ex;

        for (int i = 0; i < MAX_CAUSE_DEPTH && current.getCause() != null; i++) {
            current = current.getCause();
        }

        return current;
    }
}
