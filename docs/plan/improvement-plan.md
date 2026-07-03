# logging-starter 고도화 플랜

> 작성일: 2026-04-30  
> 목표: **버그 트래킹 중심**의 APM 수준 관측가능성(Observability) 확보, 비즈니스 로직 무침투 원칙 유지

---

## 1. 현황 진단 요약

기존 분석 자료(`docs/analysis/`)를 종합한 결과, 현재 라이브러리는 견고한 기반 아키텍처를 갖추고 있으나 아래 세 가지 관점에서 명확한 한계가 존재합니다.

| 관점 | 현황 | 한계점 |
|------|------|--------|
| **버그 트래킹** | TraceId/SpanId 생성, ERROR 레벨 자동 전환 | 동일 버그 반복 감지 불가, 에러 맥락(Breadcrumb) 부재, 민감정보 마스킹 미적용 |
| **APM** | Slow Query 감지, API 응답시간 측정 | 퍼센타일(p95/p99) 부재, N+1 감지 없음, 비즈니스 메트릭 수집 불가 |
| **비침투성** | ThreadLocal 기반 컨텍스트, Filter/Interceptor 위치 | 로깅 실패가 요청 실패로 전파되는 경로 존재, 동기 처리로 인한 응답 지연 가능성 |

### 1.1 즉시 수정이 필요한 버그 (기존 분석 기반)

아래 5개는 기능 추가에 앞서 **반드시 선행**되어야 할 결함입니다.

| 우선순위 | 파일 | 문제 | 증상 |
|----------|------|------|------|
| P0 | `SqlTraceInterceptor` | `getBoundSql()` 이중 호출 | 커넥션 풀 고갈 |
| P0 | `SqlTraceInterceptor` | `isLogging.remove()` finally 블록 외부 | 예외 시 re-entrance 보호 무력화 |
| P0 | `RequestWrapper` | 바디 크기 제한 없음 (`readAllBytes()`) | 대용량 업로드 시 OOM |
| P1 | `LoggingPropertiesHolder` | `volatile` 누락 | 멀티스레드 환경에서 프로퍼티 가시성 불안정 |
| P1 | `SqlTraceInterceptor` | 민감정보 마스킹 없음 | 카드번호·비밀번호 로그 평문 노출 (PCI-DSS 위반) |

---

## 2. 개선 방향 및 원칙

### 원칙 1: 비즈니스 로직 무침투 (Non-intrusive)

```
[Business Logic]
      │
      ↓ (호출)
[Filter / Interceptor / MyBatis Plugin]  ← 로깅 전담 레이어 (침투 금지 경계)
      │
      ↓ (비동기 전달)
[Async Log Event Queue]
      │
      ↓ (별도 스레드)
[Log Processor] → [Logback / Metrics Exporter]
```

- 로깅 코드는 **Filter·Interceptor·AOP·MyBatis Plugin** 레이어에만 존재
- 서비스·리포지토리 레이어 코드에 로깅 코드를 직접 추가하지 않음
- 로깅 처리 중 예외가 발생해도 **요청은 정상 완료**되어야 함 (Fail-safe)

### 원칙 2: 버그 트래킹 우선

- 에러는 **항상 전체 컨텍스트**와 함께 캡처 (요청 바디, SQL, 스택 트레이스)
- 동일한 버그가 반복 발생하면 **식별 가능한 지문(Fingerprint)** 으로 집계
- 에러 발생까지의 **행동 흔적(Breadcrumb)** 을 시간순으로 기록

### 원칙 3: APM 수준 메트릭

- 응답 시간의 **p50 / p95 / p99** 를 API 단위로 측정
- SQL 성능을 **Mapper ID 단위**로 추적
- Grafana 대시보드에서 **실시간 이상 감지** 가능한 구조

---

## 3. 핵심 기능 설계

### 3.1 버그 트래킹 시스템 (최우선)

#### 3.1.1 Error Fingerprinting — 동일 버그 식별

에러가 발생할 때 스택 트레이스의 핵심 프레임을 해시하여 **동일한 예외를 하나의 버그**로 식별합니다.

```
에러 발생
    │
    ▼
[ErrorFingerprinter]
  - 최상위 예외 클래스명
  - 첫 번째 자사 패키지 호출 위치 (com.company.*)
  - Root Cause 클래스명
    │
    ▼
SHA-256 → 앞 12자리 → errorFingerprint = "a3f9b2c11d04"
```

**로그 출력 예시:**
```
ERROR [SERVLET] traceId=abc123 errorFingerprint=a3f9b2c11d04 errorType=BizException
      errorMsg=잔액이 부족합니다 errorCount(session)=1
      api=POST /api/v1/payment elapsed=234ms
      req={"amount":50000,"cardNo":"****-****-****-1234"}
      sql[0]=UPDATE TBTX_BALANCE SET BALANCE=? WHERE ACCT_NO=? elapsed=12ms
```

- `errorFingerprint` 값이 같으면 Grafana에서 동일 버그로 집계 가능
- `errorCount`는 세션 내 동일 fingerprint 발생 횟수 (반복 오류 조기 감지)

#### 3.1.2 Breadcrumb Trail — 에러 발생 전 맥락 추적

요청 처리 중 주요 이벤트를 **시간순 빵 부스러기**처럼 기록합니다. 에러 발생 시에만 출력합니다.

```java
// TraceContextHolder에 추가
private static final ThreadLocal<Deque<BreadcrumbEvent>> BREADCRUMBS = new ThreadLocal<>();

// 사용 예시 (Filter, Interceptor, AOP에서만 호출)
TraceContextHolder.addBreadcrumb("SQL_EXECUTE", "SELECT TBTX_PAYMENT...");
TraceContextHolder.addBreadcrumb("CACHE_HIT", "prepaid:balance:ACC001");
TraceContextHolder.addBreadcrumb("EXTERNAL_CALL", "POST https://pg.example.com/approve");
```

**에러 시 로그 출력:**
```
ERROR breadcrumbs=[
  00ms → FILTER_ENTER: POST /api/v1/payment
  12ms → SQL_EXECUTE: SELECT TBTX_ACCOUNT... (3ms)
  15ms → SQL_EXECUTE: SELECT TBTX_BALANCE... (2ms)
  17ms → EXTERNAL_CALL: POST https://pg.example.com/approve (timeout!)
  317ms → ERROR: ReadTimeoutException
]
```

> **비침투 원칙 유지:** Breadcrumb 기록은 **MyBatis Interceptor**, **LoggingFilter** 내부에서만 호출.  
> 비즈니스 서비스 클래스는 이를 직접 호출하지 않음.

#### 3.1.3 에러 분류 체계 (ErrorClassifier)

```
Exception
  ├── BizException (예측된 비즈니스 오류)          → WARN 레벨, 짧은 스택
  ├── ExternalSystemException (외부 연동 실패)      → ERROR 레벨, 전체 컨텍스트
  ├── DatabaseException (DB 관련)                  → ERROR 레벨, SQL 포함
  └── SystemException (예상치 못한 시스템 오류)    → ERROR 레벨, 전체 스택
```

`AbstractLogProcessor`에서 예외 타입을 판단하여 **로그 레벨과 출력 범위를 자동 결정**합니다. 비즈니스 레이어는 단순히 예외를 throw하면 됩니다.

#### 3.1.4 민감정보 마스킹 (SensitiveDataMasker)

```java
public final class SensitiveDataMasker {

    // 카드번호: 앞 6자리 + **** + 마지막 4자리
    private static final Pattern CARD_NO = Pattern.compile("\\b(\\d{4}[-\\s]?\\d{2})\\d{2}[-\\s]?\\d{4}[-\\s]?(\\d{4})\\b");

    // 계좌번호, 주민등록번호 패턴
    private static final Pattern SSN = Pattern.compile("\\b(\\d{6})[-]?(\\d{7})\\b");

    public static String mask(String value) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }

        return SSN.matcher(CARD_NO.matcher(value).replaceAll("$1**-****-$2")).replaceAll("$1-*******");
    }
}
```

적용 위치: `SqlTraceInterceptor.extractSqlParam()`, `LogApiContext` 바디 캡처 시

---

### 3.2 APM 수준 메트릭 수집

#### 3.2.1 Latency Percentile Histogram (응답시간 분포)

**목표:** API별 p50·p95·p99를 Grafana에서 실시간으로 볼 수 있도록

**설계 방식 — 외부 의존 없이 Logback 기반으로 구현:**

```
[LoggingFilter / BatchListener / NettyHandler]
    │ 응답 완료 시 elapsed 기록
    ▼
[MetricsCollector] (ConcurrentHashMap + AtomicLong)
    - apiPath + httpMethod → HdrHistogram (또는 단순 배열)
    ▼
[MetricsSnapshotLogger] (@Scheduled, 1분 주기)
    - 1분마다 Logback으로 p50/p95/p99 출력
    ▼
[Grafana Alloy] → Grafana 대시보드
```

**1분 주기 메트릭 로그 예시:**
```
INFO  [METRIC] api=POST /api/v1/payment count=142 p50=87ms p95=234ms p99=891ms errorRate=0.7%
INFO  [METRIC] api=GET  /api/v1/balance  count=3241 p50=12ms p95=45ms p99=123ms errorRate=0.0%
INFO  [METRIC] sql=PaymentMapper.selectByTxId count=3241 p50=3ms p95=18ms p99=67ms slowCount=2
```

> Micrometer·Prometheus를 사용하지 않는 이유: 외부 의존성 최소화.  
> 필요 시 `MetricsCollector` 인터페이스를 두어 Micrometer 구현체를 플러그인으로 교체 가능하도록 설계.

#### 3.2.2 N+1 쿼리 자동 감지

**동일한 Mapper ID가 같은 요청 내에서 N회 이상 반복 실행되면 경고:**

```java
// SqlTraceContextHolder에 추가
Map<String, Integer> sqlIdCallCount = new HashMap<>();

// SqlTraceInterceptor에서
int count = sqlIdCallCount.merge(mapperId, 1, Integer::sum);
if (count >= properties.getLimit().getN1DetectionThreshold()) { // 기본 3회
    log.warn("[N+1 DETECTED] mapperId={} callCount={} traceId={}", mapperId, count, traceId);
}
```

**로그 출력:**
```
WARN  [N1_QUERY] traceId=abc123 mapperId=PaymentMapper.selectDetailByItem callCount=15
      → 루프 내 개별 조회 의심. fetchAll + groupBy 또는 IN절 쿼리로 리팩터링 권장
```

#### 3.2.3 외부 연동 추적 (External Call Tracking)

HTTP Client(RestTemplate/WebClient/FeignClient) 호출 시간을 추적하는 AOP 인터셉터:

```java
@Aspect
@ConditionalOnProperty(name = "log.trace.external-call", havingValue = "true")
public class ExternalCallTracingAspect {

    // RestTemplate, FeignClient, WebClient 실행 포인트컷
    @Around("execution(* org.springframework.web.client.RestTemplate.exchange(..))"
          + " || execution(* feign.Client+.execute(..))")
    public Object traceExternalCall(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            TraceContextHolder.addBreadcrumb("EXTERNAL_OK",
                extractUrl(pjp) + " " + elapsedMs(start) + "ms");
            return result;
        } catch (Exception ex) {
            TraceContextHolder.addBreadcrumb("EXTERNAL_FAIL",
                extractUrl(pjp) + " " + elapsedMs(start) + "ms ERROR=" + ex.getMessage());
            throw ex;
        }
    }
}
```

> 사용자가 명시적으로 `log.trace.external-call=true` 설정 시에만 활성화 (기본 비활성).

---

### 3.3 비침투 아키텍처 강화

#### 3.3.1 Fail-safe 로깅 (현재의 가장 큰 위험)

현재 `LoggingFilter`에서 예외 발생 시 요청이 실패할 가능성이 있습니다.

```java
// AbstractLogProcessor 공통 래퍼 추가
protected final void safeProcess(T context) {
    try {
        doProcess(context);  // 기존 로직
    } catch (Exception ex) {
        // 로깅 자체의 실패는 조용히 처리 — 비즈니스 요청에 전파 금지
        log.error("[LOGGING_INTERNAL_ERROR] traceId={} cause={}", context.getTraceId(), ex.getMessage());
    }
}
```

#### 3.3.2 비동기 로그 처리 (선택적 도입)

대용량 트래픽 환경에서 로그 I/O가 응답 시간에 영향을 주는 경우를 위한 옵션:

```yaml
log:
  async:
    enabled: false          # 기본 비활성 (동기 처리가 디버깅에 유리)
    queue-size: 1000        # 비동기 큐 크기
    overflow-strategy: DROP # 큐 초과 시 로그 드롭 (비즈니스 우선)
    thread-pool-size: 2     # 로그 전용 스레드 수
```

```
[요청 스레드] → LogEvent 생성 → [BlockingQueue] → [LogWorker 스레드] → Logback
                                  (큐 초과 시 DROP)
```

> **주의:** 비동기 전환 시 예외 발생 직전 로그가 누락될 수 있음.  
> 에러 이벤트는 **항상 동기 처리**하여 버그 트래킹 데이터 손실 방지.

#### 3.3.3 샘플링 전략 (현재 sample-rate만 존재)

```
요청 수신
    │
    ▼
에러 요청? ───YES──→ 항상 전체 로깅 (샘플링 제외)
    │
   NO
    │
    ▼
Slow 요청? ───YES──→ 항상 전체 로깅
    │
   NO
    │
    ▼
sample-rate 확률 판정
    ├── YES → PROD 레벨 로깅
    └── NO  → TraceId만 기록 (최소 로깅)
```

**구현 위치:** `LoggingFilter.doFilter()` 진입부, `NettyTraceDuplexHandler.channelRead()` 진입부

---

## 4. 단계별 구현 로드맵

### Phase 0: 선행 버그 수정 (1주 — 현재 스프린트)

> 기능 추가에 앞서 기존 결함을 제거합니다.

| # | 작업 | 파일 | 공수 |
|---|------|------|------|
| 0-1 | `getBoundSql()` 이중 호출 제거 | `SqlTraceInterceptor` | 0.5d |
| 0-2 | `isLogging.remove()` finally 블록 이동 | `SqlTraceInterceptor` | 0.5d |
| 0-3 | `RequestWrapper` 최대 바디 크기 제한 추가 | `RequestWrapper` | 0.5d |
| 0-4 | `LoggingPropertiesHolder.properties`에 `volatile` 추가 | `LoggingPropertiesHolder` | 0.5d |
| 0-5 | `SensitiveDataMasker` 구현 및 적용 | 신규 + `SqlTraceInterceptor`, `LogApiContext` | 2d |

---

### Phase 1: 버그 트래킹 기반 구축 (2주)

| # | 작업 | 신규/수정 | 공수 |
|---|------|-----------|------|
| 1-1 | `ErrorFingerprinter` 구현 | 신규 `core/error/ErrorFingerprinter.java` | 2d |
| 1-2 | `BreadcrumbEvent` + `TraceContextHolder` breadcrumb 추가 | 수정 + 신규 | 2d |
| 1-3 | `ErrorClassifier` 구현 (BizException 분류) | 신규 `core/error/ErrorClassifier.java` | 1d |
| 1-4 | `AbstractLogProcessor` fail-safe 래퍼 적용 | 수정 `AbstractLogProcessor` | 0.5d |
| 1-5 | MyBatis Interceptor → Breadcrumb 연동 | 수정 `SqlTraceInterceptor` | 1d |
| 1-6 | 에러 로그 포맷 통일 (fingerprint, breadcrumbs 포함) | 수정 각 `LogProcessor` | 1d |

**Phase 1 완료 기준:**
- 동일한 예외는 동일한 `errorFingerprint`로 식별됨
- 에러 로그에 `breadcrumbs` 필드가 포함됨
- 로깅 내부 예외가 비즈니스 요청에 전파되지 않음

---

### Phase 2: APM 메트릭 수집 (2주)

| # | 작업 | 신규/수정 | 공수 |
|---|------|-----------|------|
| 2-1 | `MetricsCollector` 인터페이스 + 기본 구현체 | 신규 `core/metrics/` | 2d |
| 2-2 | `MetricsSnapshotLogger` (1분 주기 Logback 출력) | 신규 | 1d |
| 2-3 | N+1 쿼리 자동 감지 | 수정 `SqlTraceInterceptor` | 1d |
| 2-4 | Slow API / Slow SQL 메트릭 연동 | 수정 각 `LogProcessor` | 0.5d |
| 2-5 | `ExternalCallTracingAspect` (RestTemplate/Feign) | 신규 `core/aop/` | 1.5d |
| 2-6 | Grafana 대시보드 패널 쿼리 예시 문서화 | 신규 `docs/plan/grafana-queries.md` | 1d |

**Phase 2 완료 기준:**
- Grafana에서 API별 p50/p95/p99 시각화 가능
- N+1 발생 시 WARN 로그 자동 출력
- 외부 API 호출 시간이 Breadcrumb에 기록됨

---

### Phase 3: 아키텍처 정제 (1주)

| # | 작업 | 신규/수정 | 공수 |
|---|------|-----------|------|
| 3-1 | `LoggingTaskDecorator` 익명 클래스 → `BatchLogProcessorAdapter` 분리 | 수정 | 1d |
| 3-2 | `AbstractLogProcessor` 추상 메서드 강제화 | 수정 | 0.5d |
| 3-3 | `NettyTraceDuplexHandler.write()` try-finally 추가 | 수정 | 0.5d |
| 3-4 | 비동기 처리 옵션 구현 (`log.async.enabled`) | 신규 `core/async/` | 2d |
| 3-5 | 샘플링 전략 분기 로직 통합 | 수정 `LoggingFilter`, `NettyTraceDuplexHandler` | 1d |

---

## 5. 최종 패키지 구조 (목표)

```
core/
  ├── config/           (기존)
  ├── context/          (기존)
  ├── enums/            (기존)
  ├── process/          (기존)
  ├── sql/              (기존)
  ├── support/          (기존)
  │
  ├── error/            ← Phase 1 신규
  │   ├── ErrorFingerprinter.java      # 스택 해시 → 버그 지문 생성
  │   ├── ErrorClassifier.java         # 예외 유형 분류
  │   ├── BreadcrumbEvent.java         # 단일 Breadcrumb 데이터 클래스
  │   └── SensitiveDataMasker.java     # 민감정보 마스킹
  │
  ├── metrics/          ← Phase 2 신규
  │   ├── MetricsCollector.java        # 인터페이스 (Micrometer 교체 가능)
  │   ├── InMemoryMetricsCollector.java# 기본 구현 (ConcurrentHashMap)
  │   └── MetricsSnapshotLogger.java   # 1분 주기 Logback 출력
  │
  └── async/            ← Phase 3 신규 (선택 활성화)
      ├── AsyncLogEventQueue.java      # BlockingQueue 기반 버퍼
      └── LogWorkerThread.java         # 로그 전용 처리 스레드
```

---

## 6. Grafana 연동 전략

### 6.1 LogMarker 확장 (신규 추가)

```java
public enum LogMarker {
    // 기존
    API_PROD, BATCH_PROD, SQL, SQL_SLOW, SQL_EXCEPTION,

    // Phase 1 추가 — 버그 트래킹
    ERROR_BIZ,          // 비즈니스 예외 (예측된 오류)
    ERROR_SYSTEM,       // 시스템 예외 (예상치 못한 오류)
    ERROR_EXTERNAL,     // 외부 연동 실패
    N1_QUERY,           // N+1 쿼리 감지

    // Phase 2 추가 — APM
    METRIC_API,         // API 응답시간 퍼센타일
    METRIC_SQL,         // SQL 실행시간 퍼센타일
    EXTERNAL_CALL,      // 외부 API 호출 추적
}
```

### 6.2 핵심 Grafana 패널 구성 (권장)

| 패널 | LogMarker 필터 | 시각화 유형 |
|------|----------------|-------------|
| API 에러율 | `ERROR_SYSTEM`, `ERROR_EXTERNAL` | 시계열 그래프 |
| 동일 버그 발생 빈도 | `errorFingerprint` 필드 집계 | Bar Chart |
| API 응답시간 분포 | `METRIC_API` | Heatmap |
| Slow SQL 목록 | `SQL_SLOW` | Table |
| N+1 감지 현황 | `N1_QUERY` | 로그 패널 |
| Breadcrumb 타임라인 | `traceId` 기반 검색 | Logs Panel |

---

## 7. 설정 프로퍼티 전체 설계 (목표 상태)

```yaml
log:
  trace:
    level: PROD                     # PROD | DEBUG | TRACE | ERROR

  slow:
    query-ms: 300                   # 단일 SQL 슬로우 임계값 (ms)
    query-total-ms: 1000            # 요청당 전체 SQL 슬로우 임계값 (ms)
    api-ms: 1000                    # API 응답시간 슬로우 임계값 (ms)

  limit:
    max-sql-count: 100
    max-sql-detail-count: 10
    max-sql-length: 2000
    max-sql-param-length: 1000
    max-body-length: 2000
    max-stack-depth: 5
    max-stack-lines: 3
    n1-detection-threshold: 3       # ← Phase 2 신규: N+1 감지 임계 횟수

  capture:
    body: ERROR                     # ERROR | SLOW | SAMPLE | ALWAYS
    sql: SLOW                       # SLOW | ERROR | ALWAYS
    sample-rate: 0.01

  breadcrumb:
    enabled: true                   # ← Phase 1 신규
    max-events: 50                  # 요청당 최대 Breadcrumb 이벤트 수

  external-call:
    enabled: false                  # ← Phase 2 신규: RestTemplate/Feign 추적

  async:
    enabled: false                  # ← Phase 3 신규: 비동기 로그 처리
    queue-size: 1000
    overflow-strategy: DROP         # DROP | BLOCK | DISCARD_OLDEST
    thread-pool-size: 2

  sensitive:
    masking-enabled: true           # ← Phase 0 신규: 민감정보 마스킹
    patterns:                       # 추가 마스킹 패턴 (정규식)
      - field: cardNo
        regex: "\\d{4}-?\\d{4}-?\\d{4}-?\\d{4}"
        mask: "****-****-****-{last4}"
```

---

## 8. 의사결정 근거

| 결정 | 이유 |
|------|------|
| Micrometer/Prometheus 미사용 | 외부 의존성 없는 Starter 원칙 유지. 인터페이스 분리로 필요 시 교체 가능 |
| Sentry/Bugsnag 미사용 | 라이브러리가 특정 SaaS에 종속되면 채택 장벽 증가. Logback + Grafana로 동등 효과 |
| 비동기 기본 비활성 | 디버깅 시 동기 로그가 더 유리. 성능 이슈 발생 시 설정으로 전환 가능 |
| Error Fingerprint 직접 구현 | SHA-256 12자리면 충돌률 1/2^48 — 실용적으로 충분하며 외부 의존 불필요 |
| Breadcrumb max 50개 제한 | 메모리 보호. 실제 버그 트래킹에는 마지막 50개 이벤트로 충분 |

---

## 9. 체크리스트

### Phase 0 완료 기준
- [ ] `SqlTraceInterceptor.getBoundSql()` 단일 호출로 수정
- [ ] `isLogging.remove()` finally 블록 내부 이동
- [ ] `RequestWrapper` maxBodyLength 초과 시 truncate 처리
- [ ] `LoggingPropertiesHolder.properties` volatile 선언
- [ ] `SensitiveDataMasker` 카드번호·계좌번호 마스킹 검증 테스트 통과

### Phase 1 완료 기준
- [ ] 동일 예외 발생 시 `errorFingerprint` 값이 항상 동일한지 단위 테스트
- [ ] `breadcrumbs` 필드가 에러 로그에만 출력되고 정상 로그에는 미출력
- [ ] `safeProcess()` 래퍼 적용 후 로깅 내부 예외가 요청에 전파되지 않음 확인
- [ ] 비즈니스 서비스 레이어 코드에 로깅 의존성 없음 확인

### Phase 2 완료 기준
- [ ] `MetricsSnapshotLogger` 1분 주기로 p50/p95/p99 출력 확인
- [ ] 동일 Mapper 3회 이상 호출 시 `N1_QUERY` 마커 로그 출력
- [ ] `ExternalCallTracingAspect` RestTemplate 호출 Breadcrumb 기록 확인

### Phase 3 완료 기준
- [ ] `BatchLogProcessorAdapter` 명시적 클래스로 분리, 목(Mock) 테스트 가능
- [ ] `log.async.enabled=true` 시 별도 스레드에서 로그 처리 확인
- [ ] 에러 이벤트는 비동기 활성화 시에도 동기로 즉시 처리됨 확인
