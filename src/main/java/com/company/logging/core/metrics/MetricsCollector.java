package com.company.logging.core.metrics;

import java.util.List;

/**
 * API·SQL 성능 지표를 수집하는 인터페이스입니다.
 *
 * <p>기본 구현체({@link InMemoryMetricsCollector})는 메모리 기반으로 동작하며,
 * 필요 시 Micrometer·Prometheus 구현체로 교체 가능합니다.</p>
 *
 * <p>비즈니스 로직에 침투하지 않습니다. 오직 Filter·Interceptor·AbstractLogProcessor 레이어에서만 호출합니다.</p>
 */
public interface MetricsCollector {

    /**
     * API 요청 지표를 기록합니다.
     *
     * @param method    HTTP 메서드 (GET, POST 등)
     * @param path      요청 URI
     * @param elapsedMs 처리 시간 (ms)
     * @param isError   에러 발생 여부
     */
    void recordApi(String method, String path, long elapsedMs, boolean isError);

    /**
     * SQL 실행 지표를 기록합니다.
     *
     * @param sqlId     MyBatis Mapper ID
     * @param elapsedMs 실행 시간 (ms)
     * @param isError   에러 발생 여부
     */
    void recordSql(String sqlId, long elapsedMs, boolean isError);

    /**
     * 현재까지 누적된 API 지표 스냅샷을 반환하고 내부 집계를 초기화합니다.
     */
    List<ApiMetricsSnapshot> snapshotApis();

    /**
     * 현재까지 누적된 SQL 지표 스냅샷을 반환하고 내부 집계를 초기화합니다.
     */
    List<SqlMetricsSnapshot> snapshotSqls();
}
