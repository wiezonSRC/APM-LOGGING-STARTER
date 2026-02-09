# Grafana LogQL 가이드

`logging-starter`가 생성하는 로그는 `key=value` 형태의 **logfmt** 스타일을 따릅니다. Grafana Loki에서 아래 쿼리들을 활용하여 효율적으로 모니터링할 수 있습니다.

---

## 1. 기본 추적 (Trace ID 활용)

특정 요청의 전체 흐름(API 요약, Body, SQL, 에러)을 한눈에 보려면 `trace_id`로 필터링합니다.

```logql
# 특정 트레이스 ID의 모든 로그 조회
{job="your-app"} |= "trace_id=550e8400-e29b-41d4-a716-446655440000"

# 특정 트레이스 ID의 SQL 로그만 조회
{job="your-app"} |= "trace_id=..." |= "[SQL]"
```

---

## 2. API 성능 모니터링 (PROD)

운영 환경에서 API 호출 현황 및 응답 시간을 분석합니다.

### 특정 인터페이스(IFID) 호출 내역
```logql
{job="your-app"} |= "[API_PROD]" | logfmt | interface_id = "IF-USER-001"
```

### 500ms 이상 걸린 느린 API 요약
```logql
{job="your-app"} |= "[API_PROD]" | logfmt | unwrap elapsed | elapsed > 500ms
```

### HTTP 상태 코드별 통계 (에러 발생 확인)
```logql
{job="your-app"} |= "[API_PROD]" | logfmt | status !~ "2.."
```

---

## 3. SQL 성능 및 슬로우 쿼리 분석

성능 병목이 되는 SQL을 빠르게 찾아냅니다.

### 모든 슬로우 쿼리 (개별 쿼리 기준)
```logql
{job="your-app"} |= "[SQL] (SLOW)" | logfmt
```

### 1초(1000ms) 이상 소요된 개별 SQL 필터링
```logql
{job="your-app"} |= "[SQL]" | logfmt | unwrap elapsed | elapsed > 1000ms
```

### API 전체 SQL 합계 시간이 기준치를 초과한 경우 (TOTAL_SLOW)
쿼리 하나는 빠르지만, 여러 번 호출되어 전체적으로 느려진 경우를 찾습니다.
```logql
{job="your-app"} |= "[SQL] (TOTAL_SLOW)" | logfmt
```

---

## 4. 에러 및 예외 분석

### 예외(Exception) 발생 내역 및 메시지 확인
```logql
{job="your-app"} |= "[EXCEPTION]" | logfmt
```

### 특정 에러 메시지가 포함된 예외 검색
```logql
{job="your-app"} |= "[EXCEPTION]" | logfmt | message =~ ".*NullPointerException.*"
```

---

## 💡 Grafana 대시보드 팁

1.  **Parser 사용:** 쿼리 뒤에 `| logfmt`를 붙이면 Grafana가 자동으로 필드를 추출하여 테이블 형태로 보여줍니다.
2.  **Derived Fields:** Grafana 설정에서 `trace_id` 필드에 **Internal Link**를 설정하면, 로그 내의 ID를 클릭했을 때 바로 해당 ID로 필터링된 검색 결과로 이동할 수 있습니다.
3.  **Units:** `elapsed` 필드를 시각화할 때 Unit을 `ms`로 설정하면 가독성이 좋아집니다.

---

## 로그 레벨별 식별자 요약

| 로그 유형 | 검색 키워드 | 주요 포함 정보 |
| :--- | :--- | :--- |
| **API 요약** | `[API_PROD]` | interface_id, status, elapsed, sql_count |
| **요청 본문** | `[REQ_BODY]` | request_param, request_body |
| **응답 본문** | `[RES_BODY]` | response_body |
| **개별 슬로우 쿼리** | `(SLOW)` | sql_id, elapsed, query |
| **전체 SQL 슬로우** | `(TOTAL_SLOW)` | total_sql_elapsed, interface_id |
| **예외 발생** | `[EXCEPTION]` | message, stacktrace |
