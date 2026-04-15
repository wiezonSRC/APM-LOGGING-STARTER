# dba-agent 분석 결과

> 분석 대상: logging-starter / SQL 추적 및 MyBatis 인터셉터
> 분석 일자: 2026-04-15

---

## 종합 진단 요약

총 **9개의 문제**가 발견되었습니다.

---

## 즉시 수정 필요 (High) — 3건

### 1. `isLogging.remove()` 조기 호출
- **위치**: `SqlTraceInterceptor.java:102`
- **위험**: 재진입 방어 로직 무력화, 잠재적 무한루프
- **현상**: `IS_LOGGING` ThreadLocal 플래그를 finally 블록이 아닌 중간에 `remove()`하여, CachingExecutor 재진입 방지 로직이 무력화될 수 있음
- **개선**: finally 블록에서 remove() 호출하도록 이동

### 2. `getBoundSql()` 이중 호출
- **위치**: `intercept()` finally 블록
- **위험**: TPS 3,000 환경에서 커넥션 보유 시간 연장 → 커넥션 풀 고갈 위험
- **현상**: SQL 인터셉트 시 `getBoundSql()`이 두 번 호출되어 불필요한 DB 자원 보유
- **개선**: 첫 번째 호출 결과를 로컬 변수에 캐싱하여 재사용

### 3. `beforeJob/beforeStep` 중첩 `init()` 호출
- **위치**: `LoggingBatchListener.java`
- **위험**: Job 레벨 SQL 데이터 완전 유실
- **현상**: `beforeJob()` 이후 `beforeStep()`에서 `SqlTraceContextHolder.init()`이 재호출되어 Job 단위로 수집된 SQL 컨텍스트가 초기화됨
- **개선**: Step 레벨과 Job 레벨 컨텍스트를 분리하여 관리

---

## 조기 개선 권장 (Medium) — 4건

### 4. `isDetailFull()` 판단 기준 오류
- **위치**: `SqlTraceContext.java:45`
- **내용**: 상세 SQL 카운트 초과 여부를 잘못된 기준으로 판단하여 상세 로그가 누락되거나 과다 수집될 수 있음

### 5. `@ConditionalOnProperty` 누락
- **위치**: `LoggingMybatisAutoConfiguration.java`
- **내용**: `logging.enabled=false` 설정 시에도 SQL 인터셉터가 MyBatis에 등록되어 불필요한 오버헤드 발생
- **개선**: `@ConditionalOnProperty(name = "logging.enabled", havingValue = "true", matchIfMissing = true)` 추가

### 6. SQL 이스케이프 따옴표(`''`) 미처리
- **위치**: `SQLUtil.java:66`
- **내용**: 문자열 내 이스케이프된 단일 따옴표(`''`) 처리 로직 부재로 FSM 파서 상태 오류 가능
- **개선**: `''` 패턴 감지 후 따옴표 상태를 유지하도록 FSM 수정

### 7. N+1 감지 기능 부재
- **위치**: `AbstractLogProcessor.java`
- **내용**: 동일 SQL이 반복 실행될 때 N+1 패턴을 감지하거나 경고하는 로직 없음
- **개선**: SQL 정규화 후 동일 SQL 반복 횟수를 카운팅하여 임계값 초과 시 WARN 로그 출력

---

## 개선 권고 (Low) — 2건

### 8. `LogSqlContext` 불변성 미보장
- **내용**: public setter가 노출되어 있어 SQL 컨텍스트가 외부에서 변경 가능
- **개선**: Builder 패턴 유지하되 setter 제거, 필드를 `final`로 선언

### 9. `formatValue()` 중복 구현 및 날짜 타입 커버리지 불일치
- **내용**: `SQLUtil`과 다른 클래스에서 유사한 `formatValue()` 로직이 중복 구현되어 있으며, `LocalDate`, `LocalDateTime`, `Instant` 등 Java 8+ 날짜 타입 처리가 누락됨
- **개선**: 단일 `SqlParameterFormatter` 유틸리티로 통합, Java 8 날짜 타입 추가
