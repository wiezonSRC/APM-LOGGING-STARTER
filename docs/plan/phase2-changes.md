# Phase 2 변경 사항 — APM 메트릭 & N+1 감지

## 개요

Phase 2는 로그 기반 APM(Application Performance Monitoring) 기능을 추가합니다.
`@EnableScheduling` 없이 독립 데몬 스레드로 동작하므로 비즈니스 로직에 완전히 비침투적입니다.

---

## 2-1. MetricsCollector 인터페이스 (`core/metrics/MetricsCollector.java`)

**목적:** 메트릭 수집 전략을 추상화하여 인메모리 / Micrometer / Prometheus 등으로 교체 가능하게 합니다.

**추가 메서드:**
```
void recordApi(String method, String path, long elapsedMs, boolean isError)
void recordSql(String sqlId, long elapsedMs, boolean isError)
List<ApiMetricsSnapshot> snapshotApis()
List<SqlMetricsSnapshot> snapshotSqls()
```

---

## 2-2. 스냅샷 데이터 클래스

### ApiMetricsSnapshot (`core/metrics/ApiMetricsSnapshot.java`)
- 필드: `key`, `count`, `errorCount`, `totalElapsed`, `p50Ms`, `p95Ms`, `p99Ms`
- `getErrorRate()` — 에러 비율(%) 계산 포함
- `getAvgMs()` — 평균 응답시간 계산 포함

### SqlMetricsSnapshot (`core/metrics/SqlMetricsSnapshot.java`)
- 필드: `sqlId`, `count`, `slowCount`, `errorCount`, `totalElapsed`, `p50Ms`, `p95Ms`, `p99Ms`
- `getAvgMs()` — 평균 실행시간 계산 포함

---

## 2-3. InMemoryMetricsCollector (`core/metrics/InMemoryMetricsCollector.java`)

**자료구조:**
- `ConcurrentHashMap<String, EndpointStats>` — API/SQL 키별 통계
- `LongAdder` — 락 없는 카운터 (count, errorCount, slowCount, totalElapsed)
- `ConcurrentLinkedDeque<Long>` — 최대 10,000개 샘플 (p값 계산용)

**핵심 동작:**
- `normalizePath()`: `/users/123` → `/users/{id}` 정규화 (경로 변수 집계 통합)
- `drainSamples()`: 스냅샷 시 샘플 전량 drain → 정렬 → percentile 계산
- `percentile()`: `Math.ceil(pct/100 * size) - 1` 인덱스 계산

**스냅샷 후 초기화:** `sumThenReset()` 사용으로 스냅샷과 동시에 집계값 리셋

---

## 2-4. MetricsHolder (`core/metrics/MetricsHolder.java`)

**목적:** Spring Context 외부(SqlTraceInterceptor 등)에서 MetricsCollector에 접근하는 정적 진입점

```java
private static volatile MetricsCollector collector;  // volatile로 가시성 보장
```

**주요 메서드:**
- `MetricsHolder.recordApi(method, path, elapsed, isError)` — 수집기 없으면 무시
- `MetricsHolder.recordSql(sqlId, elapsed, isError, isSlow)` — InMemoryMetricsCollector면 4-arg 오버로드 호출

---

## 2-5. MetricsSnapshotLogger (`core/metrics/MetricsSnapshotLogger.java`)

**목적:** 60초 주기로 메트릭 스냅샷을 Logback으로 출력

**특징:**
- `InitializingBean` + `DisposableBean` 구현 (Spring 빈 생명주기 활용)
- `Executors.newSingleThreadScheduledExecutor()` — 데몬 스레드 사용
- `@EnableScheduling` 불필요 — 사용자 앱 스케줄링에 무간섭

**출력 포맷 (예시):**
```
[METRIC_API] api="POST /api/v1/payment" count=142 errorRate=0.7% avg=87ms p50=82ms p95=234ms p99=891ms
[METRIC_SQL] sql_id=PaymentMapper.selectByTxId count=3241 slowCount=2 avg=3ms p50=2ms p95=18ms p99=67ms
```

---

## 2-6. N+1 감지 (`core/sql/SqlTraceInterceptor.java` + `SqlTraceContext.java`)

### SqlTraceContext 변경
```java
private final Map<String, Integer> sqlIdCallCount = new HashMap<>();

public int incrementCallCount(String sqlId) {
    return sqlIdCallCount.merge(sqlId, 1, Integer::sum);
}
```
- 요청 단위(ThreadLocal) 스코프로 Mapper ID별 호출 횟수 추적

### SqlTraceInterceptor finally 블록 추가
```java
// SQL 메트릭 기록
boolean isSlow = elapsed >= props.getSlow().getQuery().getMs();
MetricsHolder.recordSql(sqlId, elapsed, isError, isSlow);

// N+1 감지: 임계값 도달 시 한 번만 경고
if (ctx != null) {
    int callCount = ctx.incrementCallCount(sqlId);
    int threshold = props.getLimit().getN1DetectionThreshold();
    if (callCount == threshold) {
        logger.warn(LogMarker.N1_QUERY.marker(),
            "trace_id={} sql_id={} call_count={} possible N+1 detected",
            TraceContextHolder.traceId(), sqlId, callCount);
    }
}
```

**설정값:** `log.limit.n1-detection-threshold=3` (기본값, 변경 가능)

**경고 발화 조건:** `callCount == threshold` 정확히 임계값 도달 시점에만 1회 경고
(초과 호출마다 경고하지 않아 로그 폭증 방지)

---

## 2-7. API 메트릭 연동 (`servlet/process/ServletLogProcessor.java`)

`logApi()` 내 SQL 상세 로깅 직전에 API 메트릭 기록:
```java
MetricsHolder.recordApi(ctx.getMethod(), ctx.getUri(), ctx.getElapsedMs(), isError);
```

---

## 2-8. ExternalCallTracingAspect (`core/aop/ExternalCallTracingAspect.java`)

**목적:** RestTemplate 외부 HTTP 호출 시간을 Breadcrumb에 자동 기록

**포인트컷:**
```java
@Around("execution(* org.springframework.web.client.RestTemplate.*(..))")
```

**활성화 조건:**
- `log.trace.external-call=true` 설정 필요
- `spring-boot-starter-aop` 의존성 필요

**Fail-safe:** Breadcrumb 기록 실패가 외부 호출 결과에 전파되지 않도록 try-catch 처리

---

## 2-9. LoggingCoreAutoConfiguration 변경 (`core/config/LoggingCoreAutoConfiguration.java`)

추가된 Bean 등록:

| Bean | 조건 |
|------|------|
| `InMemoryMetricsCollector` | `log.metrics.enabled=true` (기본 활성화) |
| `MetricsSnapshotLogger` | `log.metrics.enabled=true` (기본 활성화) |
| `ExternalCallTracingAspect` | `log.trace.external-call=true` + AspectJ 클래스 존재 |

---

## 2-10. build.gradle 변경

```groovy
compileOnly 'org.springframework.boot:spring-boot-starter-aop'
```
AOP 의존성은 `compileOnly`로 추가 — 스타터를 사용하는 앱에서 AOP를 포함하지 않아도 컴파일 가능

---

## 추가된 LogMarker 값

| 마커 | 용도 |
|------|------|
| `N1_QUERY` | N+1 쿼리 감지 경고 |
| `METRIC_API` | API 응답시간 주기적 출력 |
| `METRIC_SQL` | SQL 실행시간 주기적 출력 |

---

## 설정 요약

```yaml
log:
  metrics:
    enabled: true           # MetricsCollector 활성화 (기본값: true)
  limit:
    n1-detection-threshold: 3   # 동일 Mapper 반복 호출 경고 임계값 (기본값: 3)
  trace:
    external-call: false    # RestTemplate Breadcrumb 추적 (기본값: false)
  slow:
    query:
      ms: 300               # 단일 쿼리 슬로우 기준 (ms)
```
