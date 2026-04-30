# Phase 0 — 선행 버그 수정 변경 내역

> 작업일: 2026-04-30  
> 브랜치: main

---

## 개요

기능 추가에 앞서 운영 환경에서 즉각적인 장애를 유발할 수 있는 5개의 결함을 수정했습니다.

---

## 수정 항목

### Fix 0-1: `SQLUtil` — BoundSql 파라미터 받는 오버로드 추가

**파일:** `core/support/sql/SQLUtil.java`

**문제:** `buildSql(MappedStatement, Object, int)` 내부에서 `ms.getBoundSql(param)`을 호출하는데, 호출부인 `SqlTraceInterceptor`에서도 `extractSqlParam()`을 위해 동일한 `getBoundSql()`을 한 번 더 호출하고 있었음.

**수정:** `buildSql(BoundSql, Object, Configuration, int)` 오버로드를 추가하여 이미 생성된 `BoundSql` 객체를 재사용할 수 있도록 함.

```java
// 추가된 오버로드
public static String buildSql(BoundSql boundSql, Object param, Configuration configuration, int maxLength)
```

---

### Fix 0-2: `SqlTraceInterceptor` — 3가지 문제 동시 수정

**파일:** `core/sql/SqlTraceInterceptor.java`

#### (a) `getBoundSql()` 이중 호출 제거

**문제:** `intercept()` finally 블록에서 `sql`과 `sqlParam` 각각을 구하기 위해 `ms.getBoundSql(param)`을 2회 호출. DB 커넥션 풀 소모 위험.

**수정:** `finally` 블록에서 `BoundSql boundSql = ms.getBoundSql(param)`을 단 한 번 호출하고, `buildSql()`과 `extractSqlParam()` 모두에 전달.

```java
// Before
sql = SQLUtil.buildSql(ms, param, maxSqlLen);       // 내부에서 getBoundSql() 호출
sqlParam = extractSqlParam(ms, param, maxParamLen); // 다시 getBoundSql() 호출

// After
BoundSql boundSql = ms.getBoundSql(param);          // 단 1회 호출
sql = SQLUtil.buildSql(boundSql, param, ms.getConfiguration(), maxSqlLen);
sqlParam = extractSqlParam(boundSql, ms.getConfiguration(), param, maxParamLen);
```

#### (b) `IS_LOGGING.remove()` 위치 이동

**문제:** `finally` 블록 **맨 앞**에서 ThreadLocal을 해제하면, 이후 처리(`extractSqlParam`, `ctx.add()` 등) 도중 예외로 인해 내부적으로 SQL이 재실행될 경우 재진입 보호가 무력화됨.

**수정:** `IS_LOGGING.remove()`를 `finally` 블록의 **맨 끝**으로 이동.

#### (c) `isLogging` → `IS_LOGGING` 상수명 수정

**문제:** `static final` 필드는 Java 컨벤션상 `SNAKE_CASE`여야 함.

**수정:** `private static final ThreadLocal<Boolean> isLogging` → `IS_LOGGING`

---

### Fix 0-3: `LoggingPropertiesHolder` — `volatile` 추가

**파일:** `core/config/LoggingPropertiesHolder.java`

**문제:** `private static LoggingProperties properties`에 `volatile`이 없으면, 멀티스레드 환경에서 `setProperties()` 직후 다른 스레드의 `getProperties()`가 `null`을 반환할 수 있음 (CPU 캐시 가시성 문제).

**수정:**
```java
// Before
private static LoggingProperties properties;

// After
private static volatile LoggingProperties properties;
```

---

### Fix 0-4: `RequestWrapper` — 바디 크기 상한 추가

**파일:** `servlet/wrapper/RequestWrapper.java`

**문제:** `request.getInputStream().readAllBytes()`는 크기 제한 없이 전체 바디를 메모리에 적재. 대용량 요청 수신 시 OOM 발생 가능.

**수정:** 1MB(`1024 * 1024`) 하드캡을 추가. `readNBytes(MAX_BODY_CACHE_BYTES)` 사용으로 최대 1MB까지만 캐싱.

```java
private static final int MAX_BODY_CACHE_BYTES = 1024 * 1024;

// Before
cachedBody = request.getInputStream().readAllBytes();

// After
cachedBody = request.getInputStream().readNBytes(MAX_BODY_CACHE_BYTES);
```

> **참고:** PG API 특성상 JSON 바디가 1MB를 초과하는 경우는 비정상 요청으로 간주. `LoggingFilter`의 바이너리 요청 가드(`isBinaryRequest`)와 함께 이중 보호.

---

### Fix 0-5: `SensitiveDataMasker` 신규 구현 및 적용

**신규 파일:** `core/support/util/SensitiveDataMasker.java`

**문제:** 카드번호·주민등록번호가 SQL 파라미터와 HTTP 바디 로그에 평문으로 노출됨 (PCI-DSS 위반).

**수정:** 정규식 기반 마스킹 유틸리티를 구현하고 3개 지점에 적용.

| 적용 위치 | 마스킹 대상 |
|----------|------------|
| `SqlTraceInterceptor.formatValue()` | SQL 파라미터 문자열 값 |
| `SQLUtil.formatValue()` | 완성된 SQL 바인딩 값 (TRACE 레벨) |
| `ServletLogProcessor.logApi()` | HTTP 요청/응답 바디 |

**마스킹 규칙:**
- 카드번호 (16자리, 하이픈/공백 허용): `1234-5678-9012-3456` → `1234-****-****-3456`
- 주민등록번호 (6자리-7자리): `900101-1234567` → `900101-*******`

---

## 체크리스트 결과

- [x] `SqlTraceInterceptor.getBoundSql()` 단일 호출로 수정
- [x] `IS_LOGGING.remove()` finally 블록 내부 이동
- [x] `RequestWrapper` maxBodyLength 초과 시 truncate 처리
- [x] `LoggingPropertiesHolder.properties` volatile 선언
- [x] `SensitiveDataMasker` 구현 및 3개 지점 적용
- [x] 컴파일 오류 없음
- [x] 전체 테스트 통과 (8개 태스크 성공)
