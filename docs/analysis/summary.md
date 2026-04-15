# 4개 에이전트 종합 분석 요약

> 분석 일자: 2026-04-15
> 분석 대상: logging-starter (Spring Boot Auto-configuration 기반 로깅 스타터 라이브러리)

---

## 에이전트별 결과 요약

| 에이전트 | 발견 건수 | 결과 파일 | 최우선 이슈 |
|---------|---------|----------|------------|
| **dba-agent** | 9건 (H:3 / M:4 / L:2) | [dba-agent-result.md](./dba-agent-result.md) | `getBoundSql()` 이중 호출 → 커넥션 풀 고갈 위험 |
| **sec-agent** | 12건 (Critical:2 / H:4 / M:3 / L:3) | [sec-agent-result.md](./sec-agent-result.md) | 민감정보 마스킹 전무 → **PCI-DSS 위반** |
| **arch-agent** | 5건 우선 개선 | [arch-agent-result.md](./arch-agent-result.md) | Netty `write()` try-finally 누락 → 채널 간 로그 오염 |
| **coach-agent** | 종합 등급 B+ | [coach-agent-result.md](./coach-agent-result.md) | `Accept` 헤더 오용, `volatile` 미적용 |

---

## 공통으로 지적된 핵심 이슈 (전 에이전트 교차 검증)

### 1. `LoggingPropertiesHolder` volatile 누락
- **지적 에이전트**: dba / sec / arch / coach (전부)
- **위험**: 멀티코어 CPU 캐시 가시성 미보장
- **즉시 수정**:
```java
private static volatile LoggingProperties properties;
```

### 2. `RequestWrapper` readAllBytes() 크기 제한 없음
- **지적 에이전트**: sec / coach
- **위험**: 대용량 요청 시 **OOM → 서비스 중단**
- **즉시 수정**: `ResponseWrapper`의 1MB 캡 패턴 동일 적용

### 3. `LoggingTaskDecorator` 익명 클래스 Hack
- **지적 에이전트**: arch / coach
- **위험**: DIP 위반, Mock 불가, 테스트 불가
- **중기 개선**: `SqlLogHelper` 독립 클래스 추출

---

## 우선순위별 Action Items

### 즉시 수정 (이번 스프린트)

| # | 항목 | 파일 |
|---|------|------|
| 1 | 민감정보 마스킹 `SensitiveDataMasker` 구현 | `SqlTraceInterceptor`, `ServletLogProcessor` |
| 2 | `RequestWrapper` maxBodyLength 제한 추가 | `RequestWrapper.java` |
| 3 | `LoggingPropertiesHolder` volatile 추가 | `LoggingPropertiesHolder.java` |
| 4 | `getBoundSql()` 이중 호출 제거 | `SqlTraceInterceptor.java` |
| 5 | `isLogging.remove()` 위치를 finally로 이동 | `SqlTraceInterceptor.java` |

### 조기 개선 (다음 스프린트)

| # | 항목 | 파일 |
|---|------|------|
| 6 | Netty `write()` try-finally 추가 | `NettyTraceDuplexHandler.java` |
| 7 | `@ConditionalOnProperty` 추가 | `LoggingMybatisAutoConfiguration.java` |
| 8 | `Accept` 헤더 → `Content-Type` 헤더로 변경 | `LoggingFilter.java` |
| 9 | `Math.random()` → `ThreadLocalRandom`으로 통일 | `LoggingFilter.java`, `NettyTraceDuplexHandler.java` |
| 10 | Log Injection 방지 sanitize 추가 | `LogMessageBuilder.java` |

### 중기 개선

| # | 항목 |
|---|------|
| 11 | `LoggingTaskDecorator` 익명 클래스 → `SqlLogHelper` 추출 |
| 12 | `AbstractLogProcessor` 템플릿 메서드 `abstract` 지정 |
| 13 | `removeOldestNormal()` LinkedList 또는 분리 큐로 전환 |
| 14 | `LogContext.getEx()` 반환 타입 `Exception` → `Throwable` |
| 15 | N+1 감지 기능 추가 |
