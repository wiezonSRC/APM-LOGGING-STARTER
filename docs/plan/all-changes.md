# logging-starter 전체 변경 내역

> Phase 2 (APM 메트릭 & N+1 감지) + Phase 3 (비침투 원칙 강화 & 비동기 로그 처리)

---

## 신규 파일 (New)

### core/metrics — APM 메트릭 수집 레이어

| 파일 | 설명 |
|------|------|
| `core/metrics/MetricsCollector.java` | 메트릭 수집 전략 인터페이스 (`recordApi`, `recordSql`, `snapshotApis`, `snapshotSqls`) |
| `core/metrics/ApiMetricsSnapshot.java` | API 응답시간 스냅샷 (count, errorCount, p50/p95/p99, avg) |
| `core/metrics/SqlMetricsSnapshot.java` | SQL 실행시간 스냅샷 (sqlId, count, slowCount, p50/p95/p99, avg) |
| `core/metrics/InMemoryMetricsCollector.java` | ConcurrentHashMap + LongAdder + ConcurrentLinkedDeque 기반 인메모리 구현체. `/users/123` → `/users/{id}` 경로 정규화. 스냅샷 시 샘플 전량 drain 후 percentile 계산 |
| `core/metrics/MetricsHolder.java` | Spring Context 외부(SqlTraceInterceptor 등)에서 MetricsCollector에 접근하는 static 진입점. `volatile` 가시성 보장 |
| `core/metrics/MetricsSnapshotLogger.java` | 60초 주기 메트릭 스냅샷 출력. `InitializingBean` + `DisposableBean`. `@EnableScheduling` 불필요 |

### core/error — 에러 분류 & Breadcrumb

| 파일 | 설명 |
|------|------|
| `core/error/BreadcrumbEvent.java` | 요청 흐름 이벤트 기록 (offset ms, type, detail) |
| `core/error/ErrorClassifier.java` | 예외 유형 분류 (`BIZ` / `EXTERNAL` / `SYSTEM`) |
| `core/error/ErrorFingerprinter.java` | 스택트레이스 기반 버그 지문(fingerprint) 생성 — 동일 버그 = 동일 fingerprint |

### core/aop — RestTemplate 추적

| 파일 | 설명 |
|------|------|
| `core/aop/ExternalCallTracingAspect.java` | `@Around("execution(* org.springframework.web.client.RestTemplate.*(..))")`. 외부 HTTP 호출 시간을 Breadcrumb에 기록. `log.trace.external-call=true` 시 활성화. fail-safe 구조 |

### core/async — 비동기 로그 처리

| 파일 | 설명 |
|------|------|
| `core/async/AsyncLogEventQueue.java` | `LinkedBlockingQueue<Runnable>` 기반 데몬 워커 큐. graceful shutdown 5초 대기. 큐 포화 시 `DROP`/`SYNC` 전략 선택 |

### core/support/util

| 파일 | 설명 |
|------|------|
| `core/support/util/SamplingDecider.java` | `LoggingFilter`와 `NettyTraceDuplexHandler`의 중복 샘플링 로직 통합. `shouldForceTrace(props, alreadyForced)` |
| `core/support/util/SensitiveDataMasker.java` | 카드번호 등 민감정보 마스킹 유틸 |

### batch/support

| 파일 | 설명 |
|------|------|
| `batch/support/BatchLogProcessorAdapter.java` | `protected logSqlDetails()`를 태스크 데코레이터가 안전하게 호출하기 위한 명명된 어댑터. 자체 fail-safe 포함 |

### docs

| 파일 | 설명 |
|------|------|
| `docs/plan/phase2-changes.md` | Phase 2 상세 변경 명세 |
| `docs/plan/phase3-changes.md` | Phase 3 상세 변경 명세 |
| `docs/plan/grafana-queries.md` | Grafana Alloy + Loki LogQL 쿼리 모음 (API·SQL·에러·N+1·대시보드) |
| `docs/plan/all-changes.md` | 이 문서 |

---

## 수정 파일 (Modified)

### core 레이어

#### `core/config/LoggingProperties.java`
- `Async` 이너 클래스 추가 (`enabled`, `queueSize`, `threadCount`, `overflowStrategy`)
- `OverflowStrategy` enum 추가 (`DROP` / `SYNC`)
- `Limit.n1DetectionThreshold` 필드 추가 (기본값 3)
- `metrics.enabled` 프로퍼티 대응 구조 추가

#### `core/config/LoggingCoreAutoConfiguration.java`
- `InMemoryMetricsCollector` 빈 등록 (`log.metrics.enabled=true`, 기본 활성화)
- `MetricsSnapshotLogger` 빈 등록 (`log.metrics.enabled=true`, 기본 활성화)
- `ExternalCallTracingAspect` 빈 등록 (`log.trace.external-call=true` + AspectJ 존재 시)

#### `core/config/LoggingPropertiesHolder.java`
- `MetricsHolder` 초기화 연동 — Properties 설정 시점에 MetricsCollector 주입

#### `core/context/TraceContextHolder.java`
- `restore(traceId, spanId, level, forceTrace, breadcrumbs)` 추가
  - 비동기 워커 스레드에서 부모 요청 컨텍스트(브레드크럼 포함)를 복원하기 위한 메서드
- `getBreadcrumbs()` — 방어 복사 반환
- `addBreadcrumb(type, detail)` — 컨텍스트 외부 호출 시 무시 처리

#### `core/enums/LogMarker.java`
- `N1_QUERY` 추가 — N+1 쿼리 감지 경고
- `METRIC_API` 추가 — API 응답시간 주기 출력
- `METRIC_SQL` 추가 — SQL 실행시간 주기 출력

#### `core/process/AbstractLogProcessor.java`
- `public abstract void logApi(T ctx)` → **`protected abstract void logApi(T ctx)`**
  - 외부 진입점은 `process(ctx)` (fail-safe 래퍼)만 노출하도록 의도 명확화
- `logException()` — ErrorFingerprinter + ErrorClassifier + Breadcrumb 기반 에러 로그 출력

#### `core/sql/SqlTraceContext.java`
- `sqlIdCallCount: Map<String, Integer>` 필드 추가
- `incrementCallCount(sqlId)` — 요청 단위 Mapper 호출 횟수 추적 (N+1 감지용)

#### `core/sql/SqlTraceInterceptor.java`
- `finally` 블록에 SQL 메트릭 기록 추가: `MetricsHolder.recordSql(sqlId, elapsed, isError, isSlow)`
- N+1 감지: `callCount == threshold` 시점에 `[N1_QUERY]` 경고 1회 발화
- `IS_LOGGING` ThreadLocal로 중복 인터셉트 방지
- `LoggingPropertiesHolder` null 체크 — Properties 미등록 환경에서도 안전

#### `core/support/sql/SQLUtil.java`
- MyBatis `BoundSql` 에서 파라미터를 포함한 실행 SQL 재구성 로직

#### `core/support/util/LogMessageBuilder.java`
- `buildSql()`, `buildTotalSlow()`, `buildSqlOmitted()`, `buildError()` 등 구조화 로그 메시지 빌더

### servlet 레이어

#### `servlet/filter/LoggingFilter.java`
- `buildApiContext()` 메서드 분리 (기존 `logUnified` 내 인라인 → 독립 메서드)
- `submitAsync()` 추가 — SQL 컨텍스트 + 브레드크럼 스냅샷 캡처 후 `AsyncLogEventQueue` 위임
- 에러 발생 시 항상 동기 처리 (async 경로 우회)
- 샘플링 로직 → `SamplingDecider.shouldForceTrace()` 위임
- `AsyncLogEventQueue asyncQueue` nullable 필드 추가 (생성자 주입)

#### `servlet/config/LoggingWebAutoConfiguration.java`
- `AsyncLogEventQueue` 조건부 빈 등록 (`log.async.enabled=true`)
- `loggingFilterRegistration` — `AsyncLogEventQueue` optional 주입 (`@Autowired(required=false)`)

#### `servlet/process/ServletLogProcessor.java`
- `logApi()` 내 `MetricsHolder.recordApi(method, uri, elapsedMs, isError)` 추가

#### `servlet/wrapper/RequestWrapper.java`
- 요청 바디 캐싱 개선 (멀티파트 등 예외 케이스 처리)

### batch 레이어

#### `batch/support/LoggingTaskDecorator.java`
- 익명 서브클래스 제거 → `BatchLogProcessorAdapter` 사용
- double-checked locking으로 어댑터 lazy 초기화 (요청마다 인스턴스 생성 제거)

#### `batch/listener/LoggingBatchListener.java`
- 배치 잡·스텝 생명주기 이벤트 로깅 (`[BATCH_PROD]` 마커)
- 동적 리스너 등록 지원

### netty 레이어

#### `netty/handler/NettyTraceDuplexHandler.java`
- `write()`: `super.write()` try-catch 추가
  - 동기 예외 시 `promise.addListener`가 발화하지 않아 발생하는 ThreadLocal 누수 방지
- 샘플링 로직 → `SamplingDecider.shouldForceTrace()` 위임

### 빌드

#### `build.gradle`
- `compileOnly 'org.springframework.boot:spring-boot-starter-aop'` 추가
  - AOP를 사용하지 않는 소비자 앱에서도 컴파일 가능

---

## 신규 설정 키 요약

```yaml
log:
  metrics:
    enabled: true                    # InMemoryMetricsCollector + MetricsSnapshotLogger 활성화 (기본: true)
  limit:
    n1-detection-threshold: 3        # 동일 Mapper 반복 호출 N+1 경고 임계값 (기본: 3)
  trace:
    external-call: false             # RestTemplate Breadcrumb 추적 (기본: false)
  async:
    enabled: false                   # 비동기 로그 처리 활성화 (기본: false)
    queue-size: 8192                 # 큐 최대 용량 (기본: 8192)
    thread-count: 1                  # 워커 스레드 수 (기본: 1)
    overflow-strategy: DROP          # 큐 포화 시 전략: DROP | SYNC (기본: DROP)
```

---

## 신규 LogMarker 요약

| 마커 | 용도 | 출력 주체 |
|------|------|-----------|
| `[N1_QUERY]` | N+1 쿼리 감지 경고 | `SqlTraceInterceptor` |
| `[METRIC_API]` | API 응답시간 주기 집계 | `MetricsSnapshotLogger` (60초) |
| `[METRIC_SQL]` | SQL 실행시간 주기 집계 | `MetricsSnapshotLogger` (60초) |
| `[ERROR_BIZ]` | 예측된 비즈니스 예외 | `AbstractLogProcessor` |
| `[ERROR_SYSTEM]` | 미분류 시스템 예외 | `AbstractLogProcessor` |
| `[ERROR_EXTERNAL]` | 외부 연동 실패 | `AbstractLogProcessor` |

---

## 변경 흐름 다이어그램

```
HTTP 요청
  └─ LoggingFilter
       ├─ SamplingDecider.shouldForceTrace()   ← [3-5 신규]
       ├─ TraceContextHolder.init()
       ├─ SqlTraceContextHolder.init()
       │
       ├─ [비즈니스 처리]
       │     └─ SqlTraceInterceptor
       │           ├─ MetricsHolder.recordSql()         ← [2-6 신규]
       │           └─ N+1 감지 → [N1_QUERY] 경고        ← [2-6 신규]
       │
       └─ finally
             ├─ (error) → logProcessor.process() [동기]
             └─ (정상 + async=true) → AsyncLogEventQueue.offer()  ← [3-4 신규]
                   └─ 워커스레드: TraceContextHolder.restore()
                                  SqlTraceContextHolder.set()
                                  logProcessor.process()

ServletLogProcessor.logApi()
  ├─ MetricsHolder.recordApi()                ← [2-7 신규]
  ├─ logSqlDetails()  (AbstractLogProcessor)
  └─ logException()   (AbstractLogProcessor)
        └─ ErrorFingerprinter + Breadcrumb    ← [신규]

MetricsSnapshotLogger (60초 데몬)
  └─ [METRIC_API] / [METRIC_SQL] 출력         ← [2-5 신규]
```
