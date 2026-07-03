package com.company.logging.core.error;

/**
 * 예외를 유형별로 분류하여 로그 마커와 심각도를 결정하는 클래스입니다.
 *
 * <p>분류 기준은 예외 클래스명에 포함된 키워드이며, Cause 체인을 최대 5단계까지 순회합니다.
 * 자사 프레임워크의 특정 예외 클래스명을 BIZ 계열 키워드에 추가하면 더 정확한 분류가 가능합니다.</p>
 *
 * <p>분류 우선순위: BIZ → DATABASE → EXTERNAL → SYSTEM (마지막 폴백)</p>
 */
public final class ErrorClassifier {

    private static final int MAX_CAUSE_DEPTH = 5;

    private ErrorClassifier() {}

    /**
     * 에러 유형 열거형입니다.
     * Grafana 로그의 error_type 필드 값으로 사용됩니다.
     */
    public enum ErrorType {
        /** 예측된 비즈니스 오류 — 잔액 부족, 유효성 오류 등 */
        BIZ("BIZ_ERROR"),
        /** DB 접근 실패 — SQL 오류, 커넥션 타임아웃 등 */
        DATABASE("DB_ERROR"),
        /** 외부 연동 실패 — PG사 API 타임아웃, 네트워크 오류 등 */
        EXTERNAL("EXTERNAL_ERROR"),
        /** 예상치 못한 시스템 오류 — NPE, ClassCastException 등 */
        SYSTEM("SYSTEM_ERROR");

        private final String label;

        ErrorType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 예외로부터 에러 유형을 결정합니다.
     *
     * @param ex 분류할 예외
     * @return 에러 유형, null이면 {@link ErrorType#SYSTEM}
     */
    public static ErrorType classify(Throwable ex) {
        if (ex == null) {
            return ErrorType.SYSTEM;
        }

        Throwable current = ex;

        for (int depth = 0; depth < MAX_CAUSE_DEPTH && current != null; depth++) {
            String className = current.getClass().getName();

            if (isBizException(className)) {
                return ErrorType.BIZ;
            }

            if (isDatabaseException(className)) {
                return ErrorType.DATABASE;
            }

            if (isExternalException(className)) {
                return ErrorType.EXTERNAL;
            }

            current = current.getCause();
        }

        return ErrorType.SYSTEM;
    }

    private static boolean isBizException(String className) {
        return className.contains("BizException")
            || className.contains("BusinessException")
            || className.contains("AppException")
            || className.contains("ValidationException")
            || className.contains("InvalidRequestException")
            || className.contains("IllegalArgumentException");
    }

    private static boolean isDatabaseException(String className) {
        return className.contains("DataAccessException")
            || className.contains("SQLException")
            || className.contains("JdbcException")
            || className.contains("PersistenceException")
            || className.contains("MyBatisSystemException")
            || className.contains("DataIntegrityViolationException")
            || className.contains("CannotAcquireLockException");
    }

    private static boolean isExternalException(String className) {
        return className.contains("TimeoutException")
            || className.contains("ConnectException")
            || className.contains("SocketException")
            || className.contains("HttpClientErrorException")
            || className.contains("HttpServerErrorException")
            || className.contains("RestClientException")
            || className.contains("WebClientResponseException")
            || className.contains("FeignException");
    }
}
