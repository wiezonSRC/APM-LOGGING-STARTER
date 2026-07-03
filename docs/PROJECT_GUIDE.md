# Logging Starter — 프로젝트 가이드

> Spring Boot Auto-Configuration 기반 **로깅·관측성(Observability) 스타터 라이브러리**
> 버전 기준선: `v1.0.29` (v1.0.28 안정화 릴리즈 + 경량 APM 확장)

---

## 1. 개요 (Overview)

`logging-starter`는 솔루션 구축·운영 환경에서 **추적성(Traceability)** 과 **생산성**을 높이기 위한 Spring Boot 자동 설정 라이브러리입니다. 소비(consumer) 애플리케이션에 의존성으로 추가하기만 하면, 별도 코드 변경 없이 HTTP/TCP/배치 요청의 전 구간 로그와 SQL 추적을 표준화된 포맷으로 남깁니다.

### 설계 원칙

| 원칙 | 의미 |
|------|------|
| **비침투(Non-intrusive)** | 소비 앱 비즈니스 코드를 수정하지 않는다. 자동 설정으로 필터·인터셉터·리스너가 투명하게 동작한다. |
| **Fail-safe** | 로깅 로직의 어떤 예외도 소비 앱으로 전파되지 않는다. 모든 처리는 `AbstractLogProcessor`에서 흡수된다. |
| **안전한 기본값** | 기본 설정만으로 운영에 바로 사용 가능하며, 메모리·성능에 부담을 주지 않는다. (언바운드 수집 구조 없음) |
| **Grafana 친화** | 모든 로그는 `key=value` 한 줄 + 정형 `LogMarker`로 출력되어 필드 추출·필터링·알림 구성이 쉽다. |

---

## 2. 지원 환경 및 요구사항 (Support)

| 항목 | 지원 |
|------|------|
| **Java** | 17 (toolchain) |
| **Spring Boot** | 3.2.x (BOM 기준) |
| **웹(서블릿)** | Spring MVC — `LoggingFilter` 자동 등록 |
| **TCP** | Netty 4.1.x — `NettyTraceDuplexHandler` (수동 파이프라인 등록) |
| **배치** | Spring Batch — `LoggingBatchListener` (리스너 등록) |
| **ORM** | MyBatis (mybatis-spring-boot-starter 3.0.x) — `SqlTraceInterceptor` 자동 플러그인 |
| **로그 구현체** | SLF4J + Logback |

> 모든 프레임워크 의존성은 `compileOnly`로 선언되어 있어, **실제 사용 여부는 소비 앱이 어떤 의존성을 제공하느냐에 따라 자동 결정**됩니다. (예: 배치를 안 쓰면 배치 자동설정은 비활성)

---

## 3. 주요 기능 (Features)

### 3.1 멀티 환경 통합 로깅
- **Spring MVC**: 요청/응답, 상태코드, 소요시간(ms), 요청 파라미터·본문(JSON) 로깅.
- **Netty TCP**: 요청/응답 객체 및 SQL 추적. head/tail 이중 핸들러로 정상 흐름과 예외 흐름을 모두 포착.
- **Spring Batch**: Job/Step 실행 정보 및 Step 내부에서 수행된 SQL 통합 로깅. 멀티스레드 Step에서도 traceId 공유.

### 3.2 계층형 추적 레벨 (`log.trace.level`)
| 레벨 | 출력 내용 |
|------|-----------|
| **PROD** (기본) | IFID, API/Batch 요약, 요청/응답 본문(JSON) |
| **TRACE** | PROD + SQL ID·파라미터·개별 수행시간 + **실제 값이 바인딩된 완성형 SQL** |
| **(ERROR 자동 승격)** | 레벨과 무관하게, 에러 발생 시 Body·SQL 상세를 자동 노출 |

### 3.3 스마트 SQL 트레이싱
- **완성형 SQL**: `?` 자리표시자 대신 실제 파라미터가 채워진 쿼리를 출력(TRACE 레벨).
- **로그 정규화**: 주석 제거·한 줄 최적화로 DBeaver 등에 복사-붙여넣기가 바로 되도록 가공.
- **슬로우 쿼리 감지**: `log.slow.query.ms` 초과 시 `[SQL_SLOW]` 마커로 기록.
- **N+1 감지**: 동일 Mapper가 임계값(`log.limit.n1-detection-threshold`, 기본 3회) 도달 시 `[N1_QUERY]` 경고를 한 번만 출력.

### 3.4 예외 추적 (Error Tracking)
- **에러 분류**(`ErrorClassifier`): 비즈니스/시스템/외부연동 오류로 구분하여 `[ERROR_BIZ]`·`[ERROR_SYSTEM]`·`[ERROR_EXTERNAL]` 마커 부여 → Grafana 패널/알림 룰 분리.
- **에러 지문**(`ErrorFingerprinter`): 동일 유형 예외를 그룹화할 수 있는 지문 생성.
- **Breadcrumb**: 요청 처리 중 주요 이벤트를 누적해 예외 발생 시 맥락 제공.

### 3.5 민감정보 마스킹 (플래그 제어)
- 카드번호·주민등록번호 등 민감정보를 로그 출력 시점에 마스킹(`SensitiveDataMasker`).
- `log.security.masking-enabled=false`로 개발/로컬에서 원문 확인 가능. 마스킹은 **렌더 시점 단일 지점**에서 수행되어 플래그가 SQL 파라미터·본문 전 경로에 일관 적용됩니다.

### 3.6 샘플링 & 트레이스 컨텍스트
- **샘플링**(`SamplingDecider`): `log.capture.sample-rate` 기반 확률적 상세 추적(`ThreadLocalRandom` 사용, 락 프리).
- **traceId/spanId**: 단일 요청 전체 Lifecycle 추적. W3C `traceparent` 헤더가 있으면 이를 승계.

> 참고: 인메모리 메트릭 수집기(주기적 p50/p95/p99 스냅샷), RestTemplate 외부호출 추적 Aspect, 자체 비동기 로그 큐는 v1.0.29에서 **제거**되었습니다. (OOM·무침습 원칙 충돌 및 표준 대체재 존재 — 메트릭은 Micrometer, 비동기는 Logback AsyncAppender 권장)

---

## 4. 아키텍처 및 코드 흐름 (Code Flow)

### 4.1 자동 설정 진입점 (`AutoConfiguration.imports`)
```
LoggingMybatisAutoConfiguration   → SqlTraceInterceptor를 MyBatis 플러그인으로 등록
LoggingCoreAutoConfiguration      → LoggingProperties 바인딩, PropertiesHolder 세팅
LoggingWebAutoConfiguration       → LoggingFilter 를 서블릿 필터로 등록 (SERVLET 웹앱 한정)
LoggingBatchAutoConfiguration     → 배치 리스너/데코레이터 지원 빈 등록
NettyLoggingAutoConfiguration     → Netty 로깅 지원 빈 등록
```
모든 자동설정은 `log.enabled`(기본 true) 조건에서 활성화됩니다.

### 4.2 공통 처리 흐름
```
[진입점]                     [컨텍스트]                 [처리]                    [출력]
LoggingFilter (HTTP)  ┐
NettyTraceDuplexHandler│→ TraceContextHolder.init()  → XxxLogProcessor        → LogMarker + SLF4J
LoggingBatchListener  ┘   SqlTraceContextHolder.init()  (AbstractLogProcessor)    (Logback)
                          │                                 │
                          │  (MyBatis 실행 시)               │  fail-safe: 모든 예외 흡수
                          └→ SqlTraceInterceptor ───────────┘  finally에서 ThreadLocal 정리
```

1. **진입점**이 요청을 가로채 `traceId`/`spanId`를 발급하고 `TraceContextHolder`·`SqlTraceContextHolder`를 초기화한다.
2. 요청 처리 중 MyBatis 쿼리는 **`SqlTraceInterceptor`** 가 가로채 수행시간·완성형 SQL·N+1 카운트를 컨텍스트에 누적한다.
3. 요청 종료 시 각 **`LogProcessor`**(서블릿/네티/배치)가 `AbstractLogProcessor.process()`를 통해 계층별 상세도로 로그를 조립·출력한다. 이 지점에서 마스킹이 플래그에 따라 적용된다.
4. `finally`에서 ThreadLocal 컨텍스트를 정리(누수 방지)하고 MDC를 비운다.

### 4.3 패키지 구조 (요약)
```
com.company.logging
├── core        # 공통: config(자동설정·프로퍼티), context, sql(인터셉터), error, process, support(util·masker·sql)
├── servlet     # Spring MVC: filter, wrapper, process
├── netty       # Netty TCP: handler, process
└── batch       # Spring Batch: listener, support(decorator/adapter), process
```

---

## 5. 설정 (Configuration)

`application.properties` (또는 yml). 모든 키는 `log` 접두어.

| 키 | 기본값 | 설명 |
|----|--------|------|
| `log.enabled` | `true` | 로깅 전체 활성화 |
| `log.trace.level` | `PROD` | `PROD` \| `TRACE` |
| `log.slow.query.ms` | `300` | 단일 쿼리 슬로우 임계값(ms) |
| `log.slow.query.total-ms` | `1000` | 요청 내 전체 SQL 합계 슬로우 임계값(ms) |
| `log.slow.api-ms` | `1000` | API 전체 슬로우 임계값(ms) |
| `log.limit.max-sql-count` | `100` | 요청당 수집 최대 SQL 수 |
| `log.limit.max-sql-detail-count` | `10` | 상세 기록 최대 SQL 수 |
| `log.limit.max-sql-length` | `2000` | SQL 문 최대 길이 |
| `log.limit.max-sql-param-length` | `1000` | SQL 파라미터 최대 길이 |
| `log.limit.max-body-length` | `2000` | 본문 최대 길이 |
| `log.limit.max-stack-depth` | `5` | Caused-by 최대 깊이 |
| `log.limit.max-stack-lines` | `3` | Cause당 스택 라인 수 |
| `log.limit.n1-detection-threshold` | `3` | N+1 경고 발생 호출 임계값 |
| `log.capture.body` | `ERROR` | 본문 캡처 모드: `ALWAYS`/`ERROR`/`SLOW`/`SAMPLE`/`OFF` |
| `log.capture.sql` | `SLOW` | SQL 캡처 모드 (동일 enum) |
| `log.capture.sample-rate` | `0.01` | 샘플링 비율(0~1) |
| `log.security.masking-enabled` | `true` | 마스킹 전체 on/off (false 시 하위 무시) |
| `log.security.mask-body` | `true` | 요청/응답 본문·파라미터 마스킹 |
| `log.security.mask-sql-param` | `true` | SQL 파라미터·SQL 텍스트 마스킹 |

---

## 6. Starter 적용 방법 (Getting Started)

### 6.1 의존성 추가 (JitPack)

**Gradle**
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.wiezonSRC:APM-LOGGING-STARTER:v1.0.29'
}
```

**Maven**
```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
<dependency>
  <groupId>com.github.wiezonSRC</groupId>
  <artifactId>APM-LOGGING-STARTER</artifactId>
  <version>v1.0.29</version>
</dependency>
```
> ⚠️ 좌표의 `wiezonSRC`(조직)와 `APM-LOGGING-STARTER`(저장소명)는 **GitHub 저장소 경로 기준**입니다. 내부 Gradle 모듈명(`logging-starter`)과 무관하게 JitPack은 저장소명으로 서빙합니다. 저장소명을 다시 변경하면 소비 프로젝트의 좌표도 함께 변경해야 합니다.

### 6.2 환경별 적용

**Spring MVC** — 별도 설정 불필요. `LoggingFilter`가 자동 등록됩니다.

**Netty TCP** — `MessageHandler` 앞에 핸들러를 추가합니다.
```java
pipeline.addLast(new NettyTraceDuplexHandler(loggingProperties, NettyServer.REAL_IP));
```

**Spring Batch** — Job 빌드 시 리스너를 등록합니다.
```java
return new JobBuilder("myJob", jobRepository)
        .listener(loggingBatchListener)
        .start(myStep)
        .build();
```

**MyBatis 수동 설정 시** — `SqlSessionFactoryBean`을 직접 등록한다면 반드시 `SqlTraceInterceptor` 플러그인을 추가하세요. (자동설정을 쓰면 불필요)

### 6.3 Logback 패턴
소비 프로젝트의 로그 패턴에 반드시 **`[%X{traceId}]`** 를 포함해야 요청 추적이 가능합니다. 마커 전용 파일 분리 등 상세 예시는 루트 [`README.md`](../README.md)와 [`GRAFANA.md`](../GRAFANA.md)를 참고하세요.

### 6.4 필수 주의사항
1. **IFID 헤더**: 모든 HTTP 요청 헤더에 `IFID`가 있어야 API 요약 로그에 정상 기록됩니다.
2. **JSON 에러 코드**: 응답 바디의 `resCode`/`res_cd`/`code` 값이 `9999`이면 자동으로 ERROR 상세 로그를 남깁니다.

---

## 7. JitPack 배포 방법 (Release)

JitPack은 **Git 태그**를 버전으로 빌드합니다. `build.gradle`의 `version` 값이 아니라 **태그명**이 소비 측 버전이 됩니다.

```bash
# 1) 변경사항을 main에 반영
git add .
git commit -m "release: v1.0.x"
git push origin main

# 2) 버전 태그 생성
git tag v1.0.x

# 3) 태그 push → JitPack이 최초 요청 시 해당 태그를 빌드
git push origin v1.0.x
```

- 소비 프로젝트는 `com.github.wiezonSRC:APM-LOGGING-STARTER:v1.0.x` 로 참조합니다.
- 최초 빌드 상태는 `https://jitpack.io/#wiezonSRC/APM-LOGGING-STARTER` 에서 확인할 수 있습니다.
- 태그는 이미 push된 커밋을 가리키므로, **코드 push → 태그 push** 순서를 지키세요.

---

## 8. 관련 문서
- [`README.md`](../README.md) — 빠른 시작 및 Logback 상세 예시
- [`SERVLET_CONFIG.md`](../SERVLET_CONFIG.md) · [`NETTY_CONFIG.md`](../NETTY_CONFIG.md) · [`BATCH_CONFIG.md`](../BATCH_CONFIG.md) — 환경별 설정
- [`GRAFANA.md`](../GRAFANA.md) — Grafana/Alloy 연동 및 마커 활용
