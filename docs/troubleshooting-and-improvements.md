# logging-starter 트러블슈팅 & 고도화 작업 내역

> 브랜치: `feature/claude-apm`
> 작업 범위: 구조적 결함 수정(Phase 3) + APM 기능 추가(Phase 2) + 보안 마스킹

---

## 1. 트러블슈팅 — 발견된 결함 및 수정

### 1-1. NettyTraceDuplexHandler.write() — ThreadLocal 누수

**문제**

```java
// AS-IS: super.write()가 동기 예외를 던지면 아래 listener가 절대 실행되지 않음
promise.addListener(future -> {
    try { ... } finally {
        clearContext(ctx);   // ← 이 줄이 실행되지 않아 ThreadLocal 영구 누수
    }
});
super.write(ctx, msg, promise);  // ← 여기서 예외 발생 시
```

Netty의 `ChannelPromise.addListener()`는 비동기 콜백이다. `super.write()`가 동기적으로 예외를 던지면 promise가 완료되지 않아 리스너가 발화하지 않는다. 결과적으로 `clearContext()`가 호출되지 않아 `TraceContextHolder`와 `SqlTraceContextHolder`의 ThreadLocal이 해당 Netty IO 스레드에 영구적으로 잔류한다.

**해결**

```java
// TO-BE: super.write() 자체를 try-catch로 감싸 동기 예외를 별도 처리
try {
    super.write(ctx, msg, promise);
} catch (Exception ex) {
    logNetty(ctx, ex);
    clearContext(ctx);  // 동기 예외에도 컨텍스트 보장
    throw ex;
}
```

---

### 1-2. LoggingTaskDecorator — 익명 서브클래스 남용

**문제**

```java
// AS-IS: 매 태스크 실행마다 BatchLogProcessor 익명 클래스를 새로 인스턴스화
new BatchLogProcessor(props) {
    public void logDetails(String tId, String sId, TraceLevel l) {
        super.logSqlDetails(tId, sId, l, false);  // protected 메서드 우회 목적
    }
}.logDetails(traceId, spanId, level);
```

문제점:
- 매 태스크마다 새 객체 생성 (불필요한 GC 압력)
- 익명 클래스로 `protected` 메서드를 우회하는 의도가 불명확
- fail-safe(예외 처리)가 없어 배치 태스크 전체가 실패할 수 있음

**해결**

```java
// TO-BE: 명명된 어댑터 클래스 + double-checked locking lazy 캐싱
public class BatchLogProcessorAdapter extends BatchLogProcessor {
    public void logSqlOnly(String traceId, String spanId, TraceLevel level) {
        try {
            logSqlDetails(traceId, spanId, level, false);
        } catch (Exception ex) {
            logger.error("[LOGGING_INTERNAL_ERROR] ...");  // fail-safe 포함
        }
    }
}

// 데코레이터에서 한 번만 생성, 이후 재사용
private volatile BatchLogProcessorAdapter adapter;

private BatchLogProcessorAdapter getAdapter(LoggingProperties props) {
    if (adapter == null) {
        synchronized (this) {
            if (adapter == null) {
                adapter = new BatchLogProcessorAdapter(props);
            }
        }
    }
    return adapter;
}
```

---

### 1-3. Servlet · Netty 샘플링 로직 중복

**문제**

`LoggingFilter`와 `NettyTraceDuplexHandler.channelRead()` 두 곳에 완전히 동일한 샘플링 블록이 존재:

```java
// 두 파일에 동일하게 존재하던 코드
if (!forceTrace && level == TraceLevel.PROD) {
    double sampleRate = properties.getCapture().getSampleRate();
    if (sampleRate > 0 && Math.random() < sampleRate) {
        forceTrace = true;
    }
}
```

한쪽만 수정하면 동작이 달라지는 잠재적 버그.

**해결** — `SamplingDecider` 유틸로 추출

```java
// TO-BE: 양쪽 모두 한 줄로 교체
forceTrace = SamplingDecider.shouldForceTrace(properties, forceTrace);
```

---

### 1-4. Netty requestData / responseData 마스킹 누락

**문제**

`ServletLogProcessor`는 `SensitiveDataMasker.mask()`를 적용하고 있었으나, `NettyLogProcessor`는 마스킹 없이 원문을 그대로 로그에 출력하고 있었다.

또한 기존 Servlet 마스킹도 `requestParam`이 빠져있었고, 설정 플래그 없이 무조건 적용되어 개발 환경에서 원문 확인이 불가했다.

**해결** — 공통 플래그 기반 마스킹으로 통일 (하단 고도화 항목 참고)

---

### 1-5. AbstractLogProcessor.logApi() — 외부 노출 접근제어자 문제

**문제**

```java
// AS-IS: process()라는 fail-safe 래퍼가 있음에도 logApi()가 public
public abstract void logApi(T ctx);
```

외부에서 `logApi()`를 직접 호출하면 예외가 비즈니스 요청으로 전파될 위험이 있다. 실제 외부 계약은 `process()` 하나여야 한다.

**해결**

```java
// TO-BE: 구현체 내부 메서드로 제한
protected abstract void logApi(T ctx);
// process() 만이 외부 진입점
public final void process(T ctx) { try { logApi(ctx); } catch ... }
```

Java는 접근제어자 완화(`protected` → `public` 오버라이드)를 허용하므로 기존 서브클래스 변경 불필요.

---

## 2. 고도화 — 추가된 기능

### 2-1. APM 인메모리 메트릭 수집

**배경**

개별 요청 로그만으로는 "지난 1분간 API p95 응답시간이 얼마였나?" 같은 집계 질문에 답하기 어렵다. Prometheus/Micrometer 없이 로그 기반으로 주기적 집계를 제공하기 위해 추가.

**구성**

```
MetricsCollector (인터페이스)
    └─ InMemoryMetricsCollector
          ├─ ConcurrentHashMap<String, EndpointStats>  — 엔드포인트별 통계
          ├─ LongAdder                                 — 락 없는 카운터
          └─ ConcurrentLinkedDeque<Long> (max 10,000)  — p값 계산용 샘플

MetricsHolder (static 진입점)           — Spring Context 외부(SqlTraceInterceptor 등)에서 접근
MetricsSnapshotLogger (60초 데몬)       — [METRIC_API] / [METRIC_SQL] 주기 출력
```

**경로 정규화**: `/users/123` → `/users/{id}` 변환으로 동일 엔드포인트를 하나로 집계.

**출력 예시**

```
[METRIC_API] api="POST /api/v1/payment" count=142 errorRate=0.7% avg=87ms p50=82ms p95=234ms p99=891ms
[METRIC_SQL] sql_id=PaymentMapper.selectByTxId count=3241 slowCount=2 avg=3ms p50=2ms p95=18ms p99=67ms
```

---

### 2-2. N+1 쿼리 자동 감지

**배경**

동일한 Mapper를 같은 요청 내에서 반복 호출하는 N+1 패턴을 런타임에 감지하여 경고.

**구현 위치**: `SqlTraceInterceptor` finally 블록 + `SqlTraceContext.incrementCallCount()`

**핵심 설계**

```java
// callCount == threshold 시점 1회만 경고 (>= 사용 시 매 호출마다 경고 → 로그 폭증)
if (callCount == threshold) {
    logger.warn(LogMarker.N1_QUERY.marker(),
        "trace_id={} sql_id={} call_count={} possible N+1 detected",
        traceId, sqlId, callCount);
}
```

```yaml
log:
  limit:
    n1-detection-threshold: 3   # 기본값, 조정 가능
```

---

### 2-3. 에러 지문(Fingerprint) + Breadcrumb 실행 흐름 추적

**배경**

에러 로그가 쌓일 때 "이게 같은 버그인가, 다른 버그인가?" 를 판단하기 어렵고, 에러 직전에 어떤 SQL/외부 호출이 있었는지 알 수 없었다.

**에러 지문 생성** (`ErrorFingerprinter`)

스택트레이스의 최초 발생 위치(`ClassName.methodName:lineNumber`)를 MD5 해시로 변환. 동일한 버그는 항상 동일한 `fingerprint` 값을 가진다. Grafana에서 fingerprint 기준 집계 → 알림 설정 가능.

**Breadcrumb 실행 경로** (`TraceContextHolder`)

요청 처리 중 SQL, 외부 호출 등 주요 이벤트를 시간순으로 누적, 에러 발생 시 함께 출력:

```
breadcrumbs=[SQL: PaymentMapper.select 12ms → EXTERNAL_CALL: /api/pg 250ms → SQL_ERROR: PaymentMapper.update 0ms]
```

---

### 2-4. RestTemplate 외부 호출 자동 Breadcrumb 기록

**배경**

외부 PG사 호출 등 RestTemplate 사용 구간의 소요 시간이 로그에 기록되지 않아 병목 분석이 어려웠다.

**구현**: `ExternalCallTracingAspect` (`@Around RestTemplate.*`)

- `log.trace.external-call=true` + AspectJ 의존성 존재 시에만 활성화
- URL 자동 추출 (String / URI / HttpRequest 타입 인자 탐색)
- Breadcrumb 기록 실패가 외부 호출 결과에 영향 주지 않도록 fail-safe 처리

---

### 2-5. 비동기 로그 처리 (AsyncLogEventQueue)

**배경**

SQL 직렬화, 에러 지문 계산, Breadcrumb 조합 등 로그 처리 비용이 HTTP 응답 지연에 직접 영향을 주는 문제.

**설계**

```
HTTP 요청 처리 완료
    │
    ├─ (에러 발생) → 동기 처리 (에러는 즉시 기록 필요)
    │
    └─ (정상 + log.async.enabled=true)
          │
          ├─ SQL 컨텍스트 스냅샷 캡처 (SqlTraceContextHolder.get())
          ├─ Breadcrumb 스냅샷 캡처 (TraceContextHolder.getBreadcrumbs())
          └─ AsyncLogEventQueue.offer(Runnable)
                    │
                    └─ 워커 스레드:
                         TraceContextHolder.restore(...)
                         SqlTraceContextHolder.set(snapshot)
                         logProcessor.process(ctx)
```

**ThreadLocal 복원 전략**: 스냅샷을 람다가 캡처하고 워커 스레드에서 복원. 스냅샷 이후 원본 ThreadLocal은 즉시 clear하므로 요청 스레드의 데이터 오염 없음.

```yaml
log:
  async:
    enabled: false           # 기본 비활성화
    queue-size: 8192
    thread-count: 1
    overflow-strategy: DROP  # DROP | SYNC
```

---

### 2-6. 민감정보 마스킹 플래그 기반 제어

**배경**

기존 Servlet은 마스킹이 무조건 적용되어 개발 환경에서 원문 확인 불가. Netty는 마스킹 자체가 누락. requestParam도 마스킹 대상에서 빠져 있었다.

**마스킹 적용 지점 통일**

| 대상 | 적용 위치 | 커버 레이어 |
|------|-----------|------------|
| requestBody, responseBody, requestParam | `ServletLogProcessor.logApi()` | Servlet |
| requestData, responseData | `NettyLogProcessor.logApi()` | Netty |
| SQL 텍스트, sqlParam | `AbstractLogProcessor.logSqlDetails()` | 공통 (3개 레이어) |

**환경별 설정**

```yaml
# application-prod.yml (기본값, 생략 가능)
log:
  security:
    masking-enabled: true

# application-local.yml
log:
  security:
    masking-enabled: false   # 원문 그대로 확인
```

---

## 3. 신규 설정 키 전체 목록

```yaml
log:
  metrics:
    enabled: true                    # APM 메트릭 수집 (기본: true)
  limit:
    n1-detection-threshold: 3        # N+1 경고 임계값 (기본: 3)
  trace:
    external-call: false             # RestTemplate Breadcrumb 추적 (기본: false)
  async:
    enabled: false                   # 비동기 로그 처리 (기본: false)
    queue-size: 8192
    thread-count: 1
    overflow-strategy: DROP          # DROP | SYNC
  security:
    masking-enabled: true            # 마스킹 전체 on/off (기본: true)
    mask-body: true                  # HTTP 바디·파라미터 마스킹
    mask-sql-param: true             # SQL 파라미터·텍스트 마스킹
```

---

## 4. 신규 LogMarker

| 마커 | 출력 조건 | 활용 |
|------|-----------|------|
| `[N1_QUERY]` | 동일 Mapper 임계값 도달 | Grafana 알림: count > 0 즉시 |
| `[METRIC_API]` | 60초 주기 데몬 출력 | p95 대시보드, 에러율 알림 |
| `[METRIC_SQL]` | 60초 주기 데몬 출력 | slowCount 추이, avg 비교 |
| `[ERROR_BIZ]` | 비즈니스 예외 (예측된 오류) | 정상 흐름 확인용 |
| `[ERROR_SYSTEM]` | 미분류 시스템 예외 | fingerprint 기준 버그 집계 |
| `[ERROR_EXTERNAL]` | 외부 연동 실패 | PG사 장애 감지 알림 |
