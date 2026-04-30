package com.company.logging.core.error;

/**
 * 요청 처리 중 발생한 단일 이벤트를 시간순으로 기록하는 Breadcrumb 데이터 클래스입니다.
 * 에러 발생 시 로그에 포함되어 "어떤 경로로 에러에 도달했는지" 역추적을 돕습니다.
 * Filter·MyBatis Interceptor 등 인프라 레이어에서만 생성되며, 비즈니스 로직에는 노출되지 않습니다.
 */
public class BreadcrumbEvent {

    private final long offsetMs;
    private final String type;
    private final String detail;

    public BreadcrumbEvent(long offsetMs, String type, String detail) {
        this.offsetMs = offsetMs;
        this.type = type;
        this.detail = detail;
    }

    public long getOffsetMs() {
        return offsetMs;
    }

    public String getType() {
        return type;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return offsetMs + "ms → " + type + ": " + detail;
    }
}
