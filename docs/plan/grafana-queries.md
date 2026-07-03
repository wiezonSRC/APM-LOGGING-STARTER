# Grafana 로그 쿼리 모음

이 파일은 Grafana Alloy + Loki 환경에서 logging-starter가 출력하는 구조화 로그를 조회하는
LogQL 쿼리 및 패널 설정 가이드입니다.

---

## 전제 조건

- **로그 수집:** Grafana Alloy가 애플리케이션 로그를 Loki로 전송
- **레이블 구성:** `app`, `env`, `level` 레이블 최소 보유
- **로그 포맷:** 각 이벤트는 `[MARKER]` 접두사로 마커를 포함

---

## 1. API 모니터링

### 1-1. 전체 API 요청 조회
```logql
{app="your-app"} |= "[API_PROD]"
| regexp `trace_id=(?P<trace_id>\S+) span_id=(?P<span_id>\S+) uri=(?P<uri>\S+) method=(?P<method>\S+) status=(?P<status>\d+) elapsed=(?P<elapsed>\d+)ms`
```

### 1-2. 특정 URI 슬로우 요청 (1초 이상)
```logql
{app="your-app"} |= "[API_PROD]"
| regexp `uri=(?P<uri>\S+) .*elapsed=(?P<elapsed>\d+)ms`
| elapsed > 1000
```

### 1-3. 5xx 에러 발생 건수 (1분 집계)
```logql
count_over_time(
  {app="your-app"} |= "[API_PROD]" |= "status=5" [1m]
)
```

### 1-4. API 응답시간 p95 (METRIC_API 마커 활용)
```logql
{app="your-app"} |= "[METRIC_API]"
| regexp `api="(?P<api>[^"]+)" count=(?P<count>\d+) .*p95=(?P<p95>\d+)ms`
```
> `MetricsSnapshotLogger`가 60초마다 출력하는 집계 로그를 활용합니다.

---

## 2. SQL 모니터링

### 2-1. 슬로우 쿼리 조회
```logql
{app="your-app"} |= "[SQL_SLOW]"
| regexp `trace_id=(?P<trace_id>\S+) sql_id=(?P<sql_id>\S+) elapsed=(?P<elapsed>\d+)ms`
```

### 2-2. SQL 에러 조회
```logql
{app="your-app"} |= "[SQL_EXCEPTION]"
```

### 2-3. 특정 Mapper의 평균 실행시간 (METRIC_SQL 활용)
```logql
{app="your-app"} |= "[METRIC_SQL]"
| regexp `sql_id=(?P<sql_id>\S+) count=(?P<count>\d+) slowCount=(?P<slowCount>\d+) .*avg=(?P<avg>\d+)ms`
| sql_id = "PaymentMapper.selectByTxId"
```

### 2-4. N+1 쿼리 감지 알림
```logql
{app="your-app"} |= "[N1_QUERY]"
| regexp `trace_id=(?P<trace_id>\S+) sql_id=(?P<sql_id>\S+) call_count=(?P<call_count>\d+)`
```
> `call_count >= n1-detection-threshold` 에 도달한 Mapper ID를 추적합니다.
> 동일 요청 내 반복 호출 패턴을 발견하면 IN 절 또는 배치 조회로 개선하세요.

---

## 3. 에러 및 버그 트래킹

### 3-1. 에러 지문(fingerprint)별 발생 횟수 집계 — 버그 그루핑 핵심
```logql
count_over_time(
  {app="your-app"} |= "[ERROR_SYSTEM]"
  | regexp `fingerprint=(?P<fingerprint>[a-f0-9]+)`
  [1h]
) by (fingerprint)
```
> 동일 `fingerprint` = 동일 버그 발생 위치. 값이 급증하면 알림을 설정합니다.

### 3-2. 비즈니스 에러 조회 (예측된 오류)
```logql
{app="your-app"} |= "[ERROR_BIZ]"
| regexp `trace_id=(?P<trace_id>\S+) fingerprint=(?P<fp>[a-f0-9]+)`
```

### 3-3. 외부 연동 실패 조회 (PG사 타임아웃 등)
```logql
{app="your-app"} |= "[ERROR_EXTERNAL]"
| regexp `trace_id=(?P<trace_id>\S+)`
```

### 3-4. 특정 traceId로 전체 요청 흐름 재구성
```logql
{app="your-app"} |= "trace_id=<여기에_traceId_입력>"
```
> `trace_id` 하나로 API → SQL → 에러 → Breadcrumb을 모두 조회할 수 있습니다.

### 3-5. Breadcrumb 포함 에러 상세 조회
```logql
{app="your-app"} |= "[ERROR_SYSTEM]" |= "breadcrumbs="
```
> `breadcrumbs=[SQL: PaymentMapper.select 12ms → EXTERNAL_CALL: /api/pg 250ms → SQL_ERROR: PaymentMapper.update 0ms]`
> 형태로 에러 발생 직전의 실행 경로를 확인할 수 있습니다.

---

## 4. 민감정보 마스킹 확인

### 4-1. 카드번호 마스킹 검증
```logql
{app="your-app"} |= "****"
| regexp `(?P<masked>\d{4}-\*{4}-\*{4}-\d{4})`
```
> 마스킹이 정상 적용된 경우에만 이 패턴이 검색됩니다.
> 실제 16자리 카드번호가 노출되면 `SensitiveDataMasker` 설정을 점검하세요.

---

## 5. 배치 모니터링

### 5-1. 배치 잡 실행 결과 조회
```logql
{app="your-app"} |= "[BATCH_PROD]"
| regexp `jobName=(?P<jobName>\S+) status=(?P<status>\S+) elapsed=(?P<elapsed>\d+)ms`
```

### 5-2. 배치 스텝별 슬로우 SQL 조회
```logql
{app="your-app"} |= "[SQL_SLOW]" |= "stepName="
```

---

## 6. 알림 룰 권장 설정 (Grafana Alerting)

| 알림 이름 | 쿼리 기준 | 조건 |
|-----------|-----------|------|
| API 에러율 급증 | `count([ERROR_SYSTEM]) / count([API_PROD])` | > 5% / 5분 |
| 특정 버그 반복 발생 | `count([ERROR_SYSTEM]) by (fingerprint)` | > 10 / 1시간 |
| N+1 쿼리 감지 | `count([N1_QUERY])` | > 0 / 발생 즉시 |
| SQL 슬로우 비율 | `count([SQL_SLOW]) / count([SQL])` | > 10% / 5분 |
| 외부 연동 실패 | `count([ERROR_EXTERNAL])` | > 5 / 1분 |

---

## 7. 대시보드 패널 구성 권장

### 요약 대시보드 (Overview)
1. **API 요청 TPS** — `count_over_time({…} |= "[API_PROD]" [1m])`
2. **에러율** — `count([ERROR_SYSTEM]) / count([API_PROD])`
3. **p95 응답시간** — `[METRIC_API]` 로그에서 p95 값 파싱
4. **슬로우 쿼리 발생 수** — `count([SQL_SLOW])`

### 에러 트래킹 대시보드 (Bug Tracking)
1. **에러 지문별 Top 10** — fingerprint 기준 count 집계
2. **에러 유형 분포** — ERROR_BIZ / ERROR_SYSTEM / ERROR_EXTERNAL 비율
3. **N+1 발생 Mapper** — N1_QUERY 로그에서 sql_id 추출
4. **최근 에러 목록** — traceId 링크로 상세 Breadcrumb 조회
