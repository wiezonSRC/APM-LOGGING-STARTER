package com.company.logging.core.support.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * OpenTelemetry 표준 규격에 맞는 Trace ID 및 Span ID 생성을 위한 유틸리티입니다.
 */
public class TraceIdUtil {

    private TraceIdUtil() {}

    /**
     * 16바이트(32자리 Hex) Trace ID를 생성합니다.
     */
    public static String generateTraceId() {
        return generateHex(16);
    }

    /**
     * 8바이트(16자리 Hex) Span ID를 생성합니다.
     */
    public static String generateSpanId() {
        return generateHex(8);
    }

    private static String generateHex(int bytes) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes; i++) {
            sb.append(String.format("%02x", random.nextInt(256)));
        }
        return sb.toString();
    }
}
