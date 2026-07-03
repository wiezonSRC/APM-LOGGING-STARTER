# Phase 3 변경 사항 — 비침투 원칙 강화 & 비동기 로그 처리

## 개요

Phase 3는 기존 구현의 구조적 결함을 보완하고 로그 처리 경로를 비동기로 확장합니다.
비즈니스 로직 스레드의 응답 지연을 최소화하는 것이 핵심 목표입니다.

---

## 3-1. BatchLogProcessorAdapter (`batch/support/BatchLogProcessorAdapter.java`)

**문제:** `LoggingTaskDecorator`가 `BatchLogProcessor`를 익명 서브클래스로 확장하여
`protected logSqlDetails()`를 우회 호출함 → 매 태스크마다 새 인스턴스 생성, 의도 불명확.

**해결:**
- 명명된 어댑터 클래스로 분리하고 자체 fail-safe(try-catch)를 포함
- `LoggingTaskDecorator`는 double-checked locking으로 어댑터를 lazy 초기화하여 재사용

```java
public class BatchLogProcessorAdapter extends BatchLogProcessor {
    public void logSqlOnly(String traceId, String spanId, TraceLevel level) {
        try {
            logSqlDetails(traceId, spanId, level, false);
        } catch (Exception ex) {
            logger.error("[LOGGING_INTERNAL_ERROR] task SQL logging failed traceId={} cause={}", traceId, ex.getMessage());
        }
    }
}
```

---

## 3-2. AbstractLogProcessor.logApi 접근제어자 (`core/process/AbstractLogProcessor.java`)

**변경:** `public abstract void logApi(T ctx)` → `protected abstract void logApi(T ctx)`

**이유:** `logApi()`는 외부 진입점이 아님 — 공개 계약은 `process(ctx)`가 담당합니다.
Java의 접근제어 완화 규칙에 따라 서브클래스는 `public`으로 오버라이드 가능하므로
기존 `BatchLogProcessor`, `ServletLogProcessor`, `NettyLogProcessor`는 수정 불필요.

---

## 3-3. NettyTraceDuplexHandler.write() ThreadLocal 누수 방지 (`netty/handler/NettyTraceDuplexHandler.java`)

**문제:** `super.write(ctx, msg, promise)`가 동기적으로 예외를 던지면
등록된 `promise.addListener`가 발화하지 않아 `clearContext()`가 호출되지 않음
→ ThreadLocal 누수 발생.

**해결:** `super.write()` 호출을 try-catch로 감싸 동기 예외 시 명시적 cleanup 수행:

```java
try {
    super.write(ctx, msg, promise);
} catch (Exception ex) {
    logNetty(ctx, ex);
    clearContext(ctx);
    throw ex;
}
```

---

## 3-4. 비동기 로그 처리 (`core/async/AsyncLogEventQueue.java`)

**목적:** 로그 처리(SQL 직렬화, 에러 지문 계산 등)를 데몬 워커 스레드로 위임하여
HTTP 응답 지연 제거.

### AsyncLogEventQueue

- `LinkedBlockingQueue<Runnable>` — 설정 가능한 큐 크기
- `Executors.newFixedThreadPool()` 기반 데몬 워커 스레드 풀
- `InitializingBean` / `DisposableBean` — Spring 빈 생명주기 연동 (graceful shutdown 5초 대기)
- 큐 가득 참 시 `overflowStrategy`: `DROP`(기본) 또는 `SYNC`(호출자 스레드 동기 처리)

### LoggingFilter 변경

에러가 없는 정상 응답에 한해 async 경로 사용:
1. `SqlTraceContextHolder.get()` — SQL 컨텍스트 스냅샷 캡처
2. `TraceContextHolder.getBreadcrumbs()` — 브레드크럼 스냅샷 캡처
3. 람다를 큐에 제출 → 워커 스레드에서 `TraceContextHolder.restore()` + `SqlTraceContextHolder.set()` 후 `logProcessor.process(ctx)` 실행
4. 에러 발생 시 항상 동기 처리 (에러 로그는 즉시 기록해야 함)

### TraceContextHolder.restore() 추가

비동기 워커 스레드에서 부모 요청 컨텍스트를 복원하는 메서드:
```java
public static void restore(String traceId, String spanId, TraceLevel level,
                           boolean forceTrace, List<BreadcrumbEvent> breadcrumbs)
```

### LoggingProperties.Async 추가

| 설정 키 | 기본값 | 설명 |
|---------|--------|------|
| `log.async.enabled` | `false` | 비동기 처리 활성화 |
| `log.async.queue-size` | `8192` | 큐 최대 용량 |
| `log.async.thread-count` | `1` | 워커 스레드 수 |
| `log.async.overflow-strategy` | `DROP` | 큐 포화 시 전략 (`DROP` / `SYNC`) |

### 빈 등록 (`servlet/config/LoggingWebAutoConfiguration.java`)

```java
@Bean
@ConditionalOnProperty(prefix = "log.async", name = "enabled", havingValue = "true")
public AsyncLogEventQueue asyncLogEventQueue(LoggingProperties properties) { ... }

@Bean
public FilterRegistrationBean<LoggingFilter> loggingFilterRegistration(
        LoggingProperties properties,
        @Autowired(required = false) AsyncLogEventQueue asyncQueue) { ... }
```

---

## 3-5. SamplingDecider (`core/support/util/SamplingDecider.java`)

**문제:** `LoggingFilter`와 `NettyTraceDuplexHandler.channelRead()`에 동일한 샘플링 블록이
중복 존재 → 변경 누락 위험.

**해결:** 정적 유틸 클래스로 추출:

```java
public final class SamplingDecider {
    public static boolean shouldForceTrace(LoggingProperties props, boolean alreadyForced) { ... }
}
```

- `alreadyForced=true`면 즉시 반환 (헤더/파라미터 강제 추적 보존)
- `TraceLevel.PROD`일 때만 `sampleRate` 적용
- 양쪽 클래스 모두 한 줄 호출로 교체

---

## 설정 요약

```yaml
log:
  async:
    enabled: false             # true 설정 시 비동기 로그 처리 활성화
    queue-size: 8192           # 큐 최대 용량 (이벤트 수)
    thread-count: 1            # 워커 스레드 수
    overflow-strategy: DROP    # DROP(기본) 또는 SYNC
```

---

## 신규 파일 목록

| 파일 | 역할 |
|------|------|
| `batch/support/BatchLogProcessorAdapter.java` | logSqlDetails 위임 어댑터 |
| `core/support/util/SamplingDecider.java` | 샘플링 결정 공통 유틸 |
| `core/async/AsyncLogEventQueue.java` | 비동기 로그 큐 + 워커 |
