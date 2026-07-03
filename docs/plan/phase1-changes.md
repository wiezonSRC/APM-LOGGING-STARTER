# Phase 1 — 버그 트래킹 기반 구축 변경 내역

> 작업일: 2026-04-30  
> 브랜치: main

---

## 개요

버그 트래킹 중심의 핵심 인프라를 구축했습니다.  
에러 발생 시 **"어디서, 어떤 경로로, 어떤 유형의 버그가 반복되는지"** 를 Grafana에서 추적할 수 있게 됩니다.

**비침투 원칙 유지:** 모든 기능은 Filter·Interceptor·AbstractLogProcessor 레이어에만 존재하며, 비즈니스 서비스 코드에는 단 한 줄의 변경도 없습니다.

---

## 신규 파일

### `core/error/BreadcrumbEvent.java`

요청 처리 중 발생한 단일 이벤트를 시간순으로 기록하는 데이터 클래스입니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| `offsetMs` | `long` | 요청 시작부터의 경과 시간 (ms) |
| `type` | `String` | 이벤트 유형 (`SQL`, `SQL_ERROR`, `EXTERNAL_CALL` 등) |
| `detail` | `String` | 상세 설명 (Mapper ID + 소요 시간 등) |

**사용 예:**
```
12ms → SQL: PaymentMapper.selectByTxId 3ms
18ms → SQL: BalanceMapper.selectBalance 5ms
320ms → SQL_ERROR: PaymentMapper.updateStatus 302ms
```

---

### `core/error/ErrorFingerprinter.java`

예외로부터 동일 버그를 식별하는 12자리 SHA-256 지문을 생성합니다.

**지문 생성 기준:**
1. 최상위 예외 클래스명
2. 스택 트레이스 중 첫 번째 자사(`com.company.*`) 프레임
3. Root Cause 예외 클래스명

**효과:** 같은 코드 경로에서 발생한 동일 예외는 항상 같은 `errorFingerprint` 값을 가집니다.

```
Grafana LogQL 예시:
sum by (errorFingerprint) (count_over_time({job="payment-api"} | logfmt | error_fingerprint != "" [5m]))
```

---

### `core/error/ErrorClassifier.java`

예외를 4가지 유형으로 자동 분류합니다.

| ErrorType | label | 분류 기준 | 예시 |
|-----------|-------|----------|------|
| `BIZ` | `BIZ_ERROR` | BizException, ValidationException 등 | 잔액 부족, 입력 오류 |
| `DATABASE` | `DB_ERROR` | DataAccessException, SQLException 등 | 쿼리 실패, 락 타임아웃 |
| `EXTERNAL` | `EXTERNAL_ERROR` | TimeoutException, FeignException 등 | PG사 API 타임아웃 |
| `SYSTEM` | `SYSTEM_ERROR` | 나머지 모든 예외 | NPE, ClassCastException |

**분류 방법:** 예외 클래스명의 키워드 매칭 (Cause 체인 최대 5단계 순회)

---

## 수정 파일

### `core/context/TraceContextHolder.java` — Breadcrumb 추가

새로운 ThreadLocal 2개를 추가했습니다.

```java
private static final ThreadLocal<Long> REQUEST_START_MS = new ThreadLocal<>();
private static final ThreadLocal<ArrayDeque<BreadcrumbEvent>> BREADCRUMBS = new ThreadLocal<>();
```

**새 메서드:**

| 메서드 | 설명 |
|--------|------|
| `addBreadcrumb(String type, String detail)` | 현재 요청에 이벤트 추가 (최대 50개, 초과 시 가장 오래된 것 제거) |
| `getBreadcrumbs()` | 기록된 이벤트 목록 반환 (방어 복사본) |

**비침투 설계:** `addBreadcrumb()`은 컨텍스트가 초기화되지 않은 경우(배치 워커 스레드, 초기화 시점) 자동으로 무시됩니다.

---

### `core/enums/LogMarker.java` — 에러 유형별 마커 추가

```java
ERROR_BIZ,       // 예측된 비즈니스 오류
ERROR_SYSTEM,    // 예상치 못한 시스템 오류
ERROR_EXTERNAL,  // 외부 연동 실패
```

Grafana에서 마커별로 패널을 분리하거나 알림 룰을 설정할 수 있습니다.

```
# Grafana LogQL 예시 — SYSTEM 에러만 PagerDuty 알림
{job="payment-api"} | logfmt | __error_marker__ = "ERROR_SYSTEM"
```

---

### `core/process/AbstractLogProcessor.java` — 2가지 강화

#### (a) `process()` — Fail-safe 진입점 추가

```java
public final void process(T ctx) {
    try {
        logApi(ctx);
    } catch (Exception ex) {
        logger.error("[LOGGING_INTERNAL_ERROR] traceId={} cause={}", ctx.getTraceId(), ex.getMessage());
    }
}
```

**효과:** 로깅 내부에서 예외가 발생해도 비즈니스 요청에는 절대 전파되지 않습니다.

모든 호출부(`LoggingFilter`, `LoggingBatchListener`, `NettyTraceDuplexHandler`)가 `logApi()` 대신 `process()`를 사용하도록 변경했습니다.

#### (b) `logException()` — fingerprint·breadcrumbs 포함

```
// Before 로그 예시
trace_id=abc123 span_id=x1y2
NullPointerException at com.company.payment.PaymentService...

// After 로그 예시
trace_id=abc123 span_id=x1y2 error_fingerprint=a3f9b2c11d04 error_type=SYSTEM_ERROR
breadcrumbs=[12ms → SQL: PaymentMapper.selectByTxId 3ms, 18ms → SQL_ERROR: BalanceMapper.lock 5ms]
NullPointerException at com.company.payment.PaymentService...
```

---

### `core/support/util/LogMessageBuilder.java` — `buildError()` 추가

에러 발생 시 fingerprint·errorType·breadcrumbs를 포함한 구조화된 로그 포맷을 생성합니다.

```java
public static String buildError(String traceId, String spanId, String fingerprint,
                                 String errorType, List<BreadcrumbEvent> breadcrumbs,
                                 Throwable ex, int maxDepth, int maxLines)
```

---

### `core/sql/SqlTraceInterceptor.java` — SQL Breadcrumb 기록

SQL 실행 완료 시 자동으로 Breadcrumb를 기록합니다.

```java
// finally 블록에서
TraceContextHolder.addBreadcrumb(
    isError ? "SQL_ERROR" : "SQL",
    sqlId + " " + elapsed + "ms"
);
```

비즈니스 코드는 이 코드를 전혀 알지 못합니다.

---

### 호출부 변경 — `logApi()` → `process()` 교체

| 파일 | 변경 |
|------|------|
| `servlet/filter/LoggingFilter.java` | `logProcessor.logApi(apiContext)` → `logProcessor.process(apiContext)` |
| `batch/listener/LoggingBatchListener.java` | `logProcessor.logApi(batchContext)` × 2 → `logProcessor.process(batchContext)` |
| `netty/handler/NettyTraceDuplexHandler.java` | `logProcessor.logApi(builder.build())` → `logProcessor.process(builder.build())` |

---

## 최종 패키지 구조 (추가 부분)

```
core/
  └── error/                  ← Phase 1 신규
      ├── BreadcrumbEvent.java
      ├── ErrorFingerprinter.java
      └── ErrorClassifier.java
```

---

## Grafana 활용 시나리오

### 1. 동일 버그 발생 빈도 추적

```logql
sum by (error_fingerprint) (
  count_over_time({job="payment-api"} | logfmt | error_fingerprint != "" [1h])
)
```

→ `a3f9b2c11d04` 지문이 오늘 100번 발생했다면 같은 코드 경로의 버그가 반복된 것.

### 2. 외부 연동 실패 집계

```logql
{job="payment-api"} | logfmt | error_type = "EXTERNAL_ERROR"
| count_over_time([5m])
```

### 3. Breadcrumb로 에러 원인 역추적

```logql
{job="payment-api"} | logfmt | trace_id = "abc123"
```

→ 특정 traceId의 전체 로그를 보면 breadcrumbs 필드로 에러 직전 SQL·외부 호출 경로 확인 가능.

---

## 체크리스트 결과

- [x] `BreadcrumbEvent` 구현
- [x] `TraceContextHolder` breadcrumb 지원 추가 (REQUEST_START_MS, BREADCRUMBS ThreadLocal)
- [x] `ErrorFingerprinter` SHA-256 기반 12자리 지문 생성
- [x] `ErrorClassifier` 4가지 유형 분류 (BIZ/DATABASE/EXTERNAL/SYSTEM)
- [x] `LogMarker` ERROR_BIZ, ERROR_SYSTEM, ERROR_EXTERNAL 추가
- [x] `AbstractLogProcessor.process()` Fail-safe 래퍼 추가
- [x] `AbstractLogProcessor.logException()` fingerprint·breadcrumbs 포함
- [x] `LogMessageBuilder.buildError()` 추가
- [x] `SqlTraceInterceptor` SQL Breadcrumb 자동 기록
- [x] 호출부 3개 파일 `process()` 교체
- [x] 컴파일 오류 없음
- [x] 전체 테스트 통과 (8개 태스크 성공)
