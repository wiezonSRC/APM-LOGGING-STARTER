# arch-agent 분석 결과

> 분석 대상: logging-starter / 시스템 아키텍처
> 분석 일자: 2026-04-15

---

## 아키텍처 다이어그램

```
┌──────────────────────────────────────────────────────────┐
│                    logging-starter                        │
│                                                          │
│  ┌───────────────────────────────────────────────────┐  │
│  │                    core 모듈                       │  │
│  │  config / context / enums / process / sql /        │  │
│  │  support (util, logback)                           │  │
│  └─────────────────────┬──────────────────────────────┘  │
│           (단방향 의존) │  ← 핵심 규칙: core는 상위 모듈을  │
│                         │    절대 참조하지 않음             │
│          ┌──────────────┼──────────────┐                  │
│          ▼              ▼              ▼                  │
│    ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│    │ servlet  │  │  batch   │  │  netty   │              │
│    │  모듈    │  │  모듈    │  │  모듈    │              │
│    └──────────┘  └──────────┘  └──────────┘              │
└──────────────────────────────────────────────────────────┘
```

---

## 잘 설계된 부분 TOP 3

### 1. 환경별 컨텍스트 전파 전략
각 런타임 모델의 특성을 정확히 이해하고 최적 전략을 적용함

| 환경 | 전략 | 이유 |
|------|------|------|
| Servlet | ThreadLocal + `OncePerRequestFilter` | 1요청 = 1스레드 모델 |
| Batch (멀티스레드 Step) | `LoggingTaskDecorator` — 부모 traceId 계승 + 자식 spanId 신규 발급 | 스레드 풀에서 컨텍스트 전파 필요 |
| Netty | Channel Attribute — 채널별 영속 상태 관리 | EventLoop 1스레드 N채널 구조 |

### 2. 단방향 모듈 의존성 철저 유지
`core ← servlet/batch/netty` 방향이 한 번도 깨지지 않아 신규 채널 추가 시 core 수정 없이 확장 가능. OCP(개방-폐쇄 원칙) 준수.

### 3. OOM 방지 다층 방어 설계
PG 운영 환경을 명확히 이해한 설계:
- SQL 개수(`maxSqlCount`), 상세 개수(`maxSqlDetailCount`), 길이 상한 분리
- 에러 쿼리 우선 보존 (`removeOldestNormal()`)
- 바이너리 요청 스킵 (`isBinaryRequest()`)
- ResponseWrapper 1MB 캡 (`MAX_CAPTURE_SIZE`)

---

## 반드시 개선해야 할 부분 (우선순위 순)

### 1순위 — `NettyTraceDuplexHandler.write()` try-finally 누락
- **위험**: Netty EventLoop 스레드 ThreadLocal 누출 → **채널 간 로그 오염**
- **현상**: `channelRead()`는 `try-finally`로 정리하지만 `write()` 처리 경로에서 예외 발생 시 ThreadLocal이 정리되지 않음
- **개선**:
```java
// TO-BE
@Override
public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    try {
        // 기존 로직
    } finally {
        clearThreadLocalIfNeeded();
    }
}
```

### 2순위 — `LoggingPropertiesHolder` volatile 누락 + 전역 static 상태
- **위험**: 멀티코어 가시성 미보장, 테스트 격리 파괴
- **개선**:
```java
// AS-IS
private static LoggingProperties properties;

// TO-BE
private static volatile LoggingProperties properties;
// 또는 AtomicReference<LoggingProperties> 사용
```

### 3순위 — Processor를 `new`로 직접 생성 (DIP 위반)
- **위치**: `LoggingTaskDecorator`
- **위험**: Mock 불가, AOP 미적용, 단위 테스트 불가
- **개선**: Spring 빈으로 주입받거나, Factory 인터페이스를 통해 생성

### 4순위 — `AbstractLogProcessor` 템플릿 메서드 미완성
- **위험**: 신규 구현체 작성 시 SQL/예외 로그 누락 가능
- **개선**: 구현 강제가 필요한 메서드를 `abstract`로 선언

### 5순위 — `@ConditionalOnProperty` 누락
- **위치**: `LoggingMybatisAutoConfiguration`
- **위험**: `logging.enabled=false` 설정 시에도 SQL 인터셉터가 항상 등록됨
- **개선**:
```java
@ConditionalOnProperty(
    name = "logging.enabled",
    havingValue = "true",
    matchIfMissing = true
)
```

---

## AutoConfiguration 설계 평가

| 항목 | 평가 | 비고 |
|------|------|------|
| `AutoConfiguration.imports` 등록 | ✅ 정상 | Spring Boot 3.x 방식 준수 |
| 모듈별 AutoConfiguration 분리 | ✅ 우수 | Core / Web / Mybatis / Batch / Netty 분리 |
| `@ConditionalOnClass` 활용 | ✅ 적절 | 클래스패스에 없는 의존성 안전 처리 |
| `@ConditionalOnProperty` 활용 | ❌ 일부 누락 | Mybatis 설정에서 누락 |
| 빈 충돌 방지 | 부분 ✅ | `@ConditionalOnMissingBean` 일부 미적용 |
