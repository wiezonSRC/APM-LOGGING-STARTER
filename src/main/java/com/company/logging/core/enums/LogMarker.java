package com.company.logging.core.enums;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public enum LogMarker {
    API_PROD,
    API_DEBUG,
    API_TRACE,
    REQ_BODY,
    RES_BODY,
    NETTY_PROD,
    NETTY_DATA,
    BATCH_PROD,
    SQL,
    SQL_SLOW,
    SQL_EXCEPTION,
    EXCEPTION,

    // Phase 1 추가 — 에러 유형별 마커 (Grafana 패널 분리 및 알림 룰 적용 목적)
    ERROR_BIZ,       // 예측된 비즈니스 오류 (잔액 부족, 유효성 오류 등)
    ERROR_SYSTEM,    // 예상치 못한 시스템 오류 (NPE, ClassCastException 등)
    ERROR_EXTERNAL,  // 외부 연동 실패 (PG사 API 타임아웃, 네트워크 오류 등)

    // Phase 2 추가 — APM 메트릭 마커
    N1_QUERY,        // N+1 쿼리 자동 감지 경고
    METRIC_API,      // API 응답시간 p50/p95/p99 주기적 출력
    METRIC_SQL;      // SQL 실행시간 p50/p95/p99 주기적 출력

    private final Marker marker;

    LogMarker() {
        this.marker = MarkerFactory.getMarker(this.name());
    }

    public Marker marker() {
        return marker;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
