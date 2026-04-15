# coach-agent 분석 결과

> 분석 대상: logging-starter / 코드 품질 (한국 대형 IT 기업 기술 면접 기준)
> 분석 일자: 2026-04-15
> **종합 등급: B+**

---

## 프로젝트 핵심 흐름 (4개 레이어)

```
1. 컨텍스트 초기화  → LoggingFilter / NettyTraceDuplexHandler / LoggingBatchListener
2. SQL 추적         → SqlTraceInterceptor (MyBatis Executor 인터셉트)
3. 로그 출력        → AbstractLogProcessor<T> 템플릿 + 각 도메인 구현체
4. 비동기 전파      → LoggingTaskDecorator (부모 → 자식 스레드 MDC 전파)
```

---

## 알고리즘 / 자료구조 복잡도 분석

| 위치 | 자료구조 / 알고리즘 | 시간복잡도 |
|------|---------------------|-----------|
| `SqlTraceContext` | `ArrayList<LogSqlContext>` | add: O(1) |
| `removeOldestNormal()` | 선형 탐색 + 중간 삭제 | O(n) |
| `SQLUtil.replacePlaceholders()` | FSM 파서 (단일 패스) | O(L) |
| `TraceIdUtil.generateHex()` | `ThreadLocalRandom` | O(1) |
| `hasErrorCode()` JSON 파싱 | DFS 재귀 순회 | O(N) — 스택오버플로우 위험 |

### `removeOldestNormal()` 최적화 제안
```java
// AS-IS: ArrayList 중간 삭제 O(n)

// TO-BE 방안 1: LinkedList 기반 O(1) 삭제
private final LinkedList<LogSqlContext> traces = new LinkedList<>();

public void removeOldestNormal() {
    Iterator<LogSqlContext> it = traces.iterator();
    while (it.hasNext()) {
        if (!it.next().isError()) {
            it.remove(); // O(1)
            return;
        }
    }
    if (!traces.isEmpty()) traces.removeFirst();
}

// TO-BE 방안 2: 에러/정상 큐 분리 (O(1) 보장)
private final Deque<LogSqlContext> normalQueue = new ArrayDeque<>();
private final Deque<LogSqlContext> errorQueue  = new ArrayDeque<>();
```

---

## 강점 (잘된 점)

| 항목 | 상세 |
|------|------|
| 에러 쿼리 우선 보존 전략 | 운영 장애 경험 없이 떠올리기 어려운 아이디어 |
| `AbstractLogProcessor<T>` 멀티 프로토콜 추상화 | OCP 준수, 신규 프로토콜 core 수정 없이 확장 |
| ThreadLocal `try-finally` 생명주기 관리 | 모든 진입점에서 정확한 `remove()` 처리 |
| `SQLUtil.replacePlaceholders()` FSM 파서 | O(L) 단일 패스, 따옴표/주석 내 `?` 정확 처리 |
| Netty Channel Attribute 사용 | EventLoop N:채널 구조 정확히 이해 |
| W3C `traceparent` 표준 지원 | OpenTelemetry 생태계 호환성 고려 |

---

## 개선 필요 (감점 요소)

### [HIGH] `Accept` 헤더 오용 — HTTP 스펙 오해
```java
// AS-IS: Accept는 클라이언트가 원하는 응답 타입 협상 헤더 (RFC 7231 §5.3.2)
//        요청 바디 타입과 무관
String accept = req.getHeader("Accept");
if (accept != null && accept.contains("application/octet-stream")) return true;

// TO-BE: Content-Type으로 요청 바디 타입 판단
private boolean isBinaryRequest(HttpServletRequest req) {
    String contentType = req.getContentType();
    if (contentType == null) return false;
    return contentType.contains("multipart/form-data")
        || contentType.contains("application/octet-stream");
}
```

### [HIGH] `LoggingPropertiesHolder` volatile 미적용
```java
// AS-IS: 멀티코어 환경에서 가시성 미보장
private static LoggingProperties properties;

// TO-BE
private static volatile LoggingProperties properties;
```

### [MEDIUM] `LoggingTaskDecorator` 익명 클래스 Hack
```java
// AS-IS: protected 접근 우회를 위한 즉석 서브클래싱 (라이브러리 코드에 부적절)
new BatchLogProcessor(props) {
    public void logDetails(String tId, String sId, TraceLevel l) {
        super.logSqlDetails(tId, sId, l, false);
    }
}.logDetails(traceId, spanId, level);

// TO-BE: SqlLogHelper 독립 클래스로 추출
// SqlLogHelper.logSqlDetails(traceId, spanId, level, false, props);
```

### [MEDIUM] `@Slf4j` 미적용 + static final 네이밍 위반
```java
// AS-IS
protected final Logger logger = LoggerFactory.getLogger("Log"); // 컨벤션 위반
private static final ThreadLocal<Boolean> isLogging = ...;      // camelCase 위반

// TO-BE
// static final은 SNAKE_CASE 적용
private static final ThreadLocal<Boolean> IS_LOGGING = ThreadLocal.withInitial(() -> false);
```

### [MEDIUM] `LogContext.getEx()` 타입 안전성
```java
// AS-IS: Error 계열(OutOfMemoryError 등) 처리 불가
public interface LogContext {
    Exception getEx();
}

// TO-BE
public interface LogContext {
    Throwable getEx();
}
```

---

## 엣지 케이스 처리 현황

| 엣지 케이스 | 처리 여부 | 비고 |
|------------|---------|------|
| RequestWrapper OOM (대용량 바디) | ❌ | `readAllBytes()` 크기 제한 없음 — 핵심 버그 |
| ResponseWrapper 1MB 캡 | ✅ | `MAX_CAPTURE_SIZE` 상수 적용 |
| ThreadLocal 메모리 누수 방지 | ✅ | 모든 진입점 `finally` 블록에 `remove()` 존재 |
| CachingExecutor 이중 인터셉트 방지 | ✅ | `IS_LOGGING` ThreadLocal 재진입 방지 |
| Batch 멀티스레드 + TaskDecorator 충돌 | 부분 ✅ | `beforeStep()` + `TaskDecorator` 동시 사용 시 컨텍스트 유실 가능 |
| 깊게 중첩된 JSON 재귀 파싱 | ❌ | `containsErrorCode()` 깊이 제한 없음 → StackOverflow 위험 |
| `System.identityHashCode()` 충돌 (Netty) | ❌ | 다른 객체 동일 hashCode 가능 |
| `Math.random()` vs `ThreadLocalRandom` 혼용 | ❌ | 고부하 환경 CAS 경쟁 발생 |

---

## 면접 핵심 Q&A

### Q1. ThreadLocal 메모리 누수 원리 설명
> **핵심 포인트**: Thread → ThreadLocalMap → Entry(WeakRef key, **Strong-ref value**) 구조.
> key(ThreadLocal)는 WeakRef라 GC 가능하지만, value는 강참조라 스레드가 살아있는 한 GC 불가.
> 스레드 풀 환경에서 `remove()` 미호출 시 이전 요청의 컨텍스트가 다음 요청에 오염됨.

### Q2. `removeOldestNormal()` 최적화
> ArrayList 중간 삭제 = O(n) 시프트. LinkedList Iterator `remove()` = O(1).
> 더 나아가 에러 큐/정상 큐 분리로 O(1) 보장 가능.

### Q3. Netty에서 Channel Attribute를 사용한 이유
> EventLoop = 단일 스레드이지만 수천 개 채널을 처리.
> ThreadLocal에 저장하면 채널 A와 B의 컨텍스트가 혼재됨.
> Channel Attribute는 채널 인스턴스에 바인딩 → 채널별 격리 보장.

### Q4. `volatile` 없는 static 필드 위험성
> Java Memory Model(JMM)에서 `volatile` 없는 static 필드는 CPU 캐시에 의해 스테일 데이터가 읽힐 수 있음.
> `volatile`은 happens-before 보장으로 항상 메인 메모리에서 최신값 읽기 강제.

---

## 역량 별점

| 영역 | 점수 |
|------|------|
| 기술 역량 | ⭐⭐⭐⭐☆ |
| 문제 해결력 | ⭐⭐⭐⭐⭐ |
| 코드 품질 | ⭐⭐⭐☆☆ |
| Spring 숙련도 | ⭐⭐⭐⭐☆ |
| CS 기본기 | ⭐⭐⭐⭐☆ |

**종합: B+ — 카카오/네이버 시니어 기준 "추가 심층 면접 후 합격 가능(Hold)"**
