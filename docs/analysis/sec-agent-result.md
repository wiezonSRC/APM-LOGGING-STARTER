# sec-agent 분석 결과

> 분석 대상: logging-starter / 보안 취약점
> 분석 일자: 2026-04-15
> **종합 보안 점수: 42 / 100**

---

## 취약점 요약 (총 12건)

| 등급 | 건수 |
|------|------|
| CRITICAL | 2 |
| HIGH | 4 |
| MEDIUM | 3 |
| LOW | 3 |

---

## CRITICAL — 즉시 조치 필요

### 1. 민감 데이터 마스킹 전무
- **위치**: `SqlTraceInterceptor.extractSqlParam()`, `ServletLogProcessor.logApi()`
- **내용**: 카드번호, 비밀번호, 계좌번호 등이 평문으로 로그에 기록됨
- **규정 위반**: **PCI-DSS 위반**
- **개선**: `SensitiveDataMasker` 유틸리티 구현
```java
// 마스킹 대상 필드 예시
private static final Set<String> SENSITIVE_KEYS = Set.of(
    "cardNo", "cvv", "password", "accountNo", "ssn", "pan"
);

public static String mask(String key, String value) {
    if (SENSITIVE_KEYS.contains(key)) return "***MASKED***";
    return value;
}
```

### 2. `RequestWrapper` HTTP Body 크기 제한 부재
- **위치**: `RequestWrapper.java`
- **내용**: `readAllBytes()` 호출 시 Content-Length 제한 없음 → 대용량 요청 시 **OOM/DoS** 위험
- **개선**: `ResponseWrapper`의 `MAX_CAPTURE_SIZE = 1MB` 패턴을 동일하게 적용
```java
// TO-BE
int maxLen = properties.getLimit().getMaxBodyLength();
byte[] buffer = request.getInputStream().readNBytes(maxLen + 1);
this.cachedBody = buffer.length > maxLen
    ? Arrays.copyOf(buffer, maxLen)
    : buffer;
```

---

## HIGH

### 3. `X-Debug-Trace` 헤더 무인증 활성화
- **내용**: 특정 헤더만 있으면 디버그 모드가 활성화되어 민감 SQL 정보가 추가 노출될 수 있음
- **개선**: IP 화이트리스트 검증 또는 내부 토큰 검증 추가

### 4. Log Injection (CWE-117)
- **내용**: 외부 입력값(URL 파라미터, HTTP 헤더)이 sanitization 없이 로그에 직접 기록
- **위험**: CRLF injection으로 로그 위변조 가능
- **개선**: 로그 기록 전 `\r`, `\n` 문자 제거
```java
private String sanitizeForLog(String value) {
    if (value == null) return null;
    return value.replaceAll("[\r\n]", "_");
}
```

### 5. ResponseWrapper OOM 위험
- **내용**: `MAX_CAPTURE_SIZE`가 상수로 하드코딩되어 있어 설정 변경 불가
- **개선**: `LoggingProperties`를 통해 외부 설정 가능하도록 변경

### 6. `volatile` 미적용 멀티스레드 가시성 문제
- **위치**: `LoggingPropertiesHolder.java`
- **내용**: `static` 필드에 `volatile` 누락으로 멀티코어 환경에서 스테일 데이터 읽기 가능
- **개선**: `private static volatile LoggingProperties properties;`

---

## MEDIUM

### 7. ThreadLocal 스레드 풀 재사용 시 컨텍스트 누수
- **내용**: 일부 예외 경로에서 `finally` 블록의 `remove()` 미호출 가능성
- **개선**: 모든 코드 경로에서 `finally` 블록 보장 여부 재검토

### 8. Netty 청크 데이터 추적 소실
- **내용**: HTTP/2 또는 청크 인코딩 응답에서 일부 청크가 누락될 수 있음

### 9. `System.identityHashCode()` 해시 충돌
- **내용**: 서로 다른 객체가 동일 hashCode를 반환할 수 있어 Netty 중복 방지 로직이 오동작 가능

---

## LOW

### 10. `Math.random()` vs `ThreadLocalRandom` 혼용
- **내용**: TraceIdUtil은 `ThreadLocalRandom`, 샘플링은 `Math.random()` 사용 — 고부하 시 CAS 경쟁 발생
- **개선**: 전체를 `ThreadLocalRandom.current().nextDouble()`으로 통일

### 11. 바이너리 판단 로직 오류 (`Accept` 헤더 오용)
- **내용**: 요청 바디 타입 판단에 `Accept` 헤더(응답 협상용) 사용 — HTTP 스펙 위반
- **개선**: `Content-Type` 헤더로 판단

### 12. `ObjectMapper` 독자 생성
- **내용**: 매 요청마다 `new ObjectMapper()` 생성 — 불필요한 객체 생성 비용
- **개선**: `static final ObjectMapper MAPPER = new ObjectMapper()` 또는 Spring 빈 주입
