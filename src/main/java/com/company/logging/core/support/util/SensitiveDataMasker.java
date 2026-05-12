package com.company.logging.core.support.util;

import java.util.regex.Pattern;

/**
 * 로그 출력 전 민감 정보를 마스킹하는 유틸리티 클래스입니다.
 * PCI-DSS 요구사항에 따라 카드번호, 주민등록번호 등 식별 가능한 패턴을 자동으로 치환합니다.
 * 비즈니스 로직에는 영향을 주지 않으며, 오직 로그 출력 직전에만 호출해야 합니다.
 */
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {}

    // 카드번호: 16자리 숫자 (붙여쓰기, 하이픈, 공백 구분자 모두 처리)
    // 앞 4자리와 뒤 4자리만 남기고 중간 8자리를 마스킹
    private static final Pattern CARD_NUMBER = Pattern.compile(
        "\\b(\\d{4})[\\s-]?(\\d{4})[\\s-]?(\\d{4})[\\s-]?(\\d{4})\\b"
    );

    // 주민등록번호: 6자리-7자리 형식
    // 생년월일 6자리만 남기고 뒤 7자리 마스킹
    private static final Pattern RRN = Pattern.compile(
        "\\b(\\d{6})-?(\\d{7})\\b"
    );

    /**
     * 입력 문자열에서 민감 정보 패턴을 찾아 마스킹하여 반환합니다.
     * null 또는 빈 문자열은 그대로 반환합니다.
     *
     * @param value 마스킹할 원본 문자열
     * @return 마스킹된 문자열
     */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // 카드번호를 먼저 마스킹한 후 주민등록번호를 마스킹
        // (카드번호 패턴이 더 구체적이므로 선행 처리)
        String result = CARD_NUMBER.matcher(value).replaceAll("$1-****-****-$4");
        result = RRN.matcher(result).replaceAll("$1-*******");

        return result;
    }

    /**
     * enabled 플래그에 따라 조건부로 마스킹을 적용합니다.
     * log.security.masking-enabled=false 환경(개발·로컬)에서 원문 확인이 필요할 때 사용합니다.
     *
     * @param value   마스킹할 원본 문자열
     * @param enabled true 이면 mask() 적용, false 이면 원본 반환
     * @return 마스킹 결과 또는 원본 문자열
     */
    public static String maskIfEnabled(String value, boolean enabled) {
        return enabled ? mask(value) : value;
    }
}
