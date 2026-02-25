# 🚀 Logging Starter

솔루션 구축 및 운영 환경에서 **추적성(Traceability)**과 **생산성**을 극대화하기 위한 Spring Boot Auto Configuration 라이브러리입니다.

## 🌟 주요 특징

### 1. 멀티 환경 지원 (Multi-Environment Support)
하나의 라이브러리로 다양한 애플리케이션 환경을 통합 로깅합니다.
- **Spring MVC**: `LoggingFilter`를 통한 서블릿 기반 HTTP 로깅.
- **Netty TCP**: `NettyTraceDuplexHandler`를 통해 TCP 서버의 요청/응답 객체 및 SQL 추적.
- **Spring Batch**: `LoggingBatchListener`를 통해 Job/Step 실행 정보 및 처리된 SQL 통합 로깅.

### 2. 고정밀 측정 및 계층형 로깅
- **나노초(ns) 정밀도**: `System.nanoTime()`을 기반으로 소수점 3자리 밀리초(`0.001ms`) 단위까지 실행 시간을 정밀하게 측정합니다.
- **계층형 상세도 조절**:
    - **PROD**: `IFID`, API/Batch 요약, 요청/응답 본문(JSON) 출력.
    - **DEBUG**: PROD 정보 + SQL ID, 파라미터, 개별 쿼리 수행 시간.
    - **TRACE**: DEBUG 정보 + **실제 값이 바인딩된 완성형 SQL문**.
    - **ERROR**: 레벨에 상관없이 에러 발생 시 상세 정보(Body, SQL)를 자동으로 노출.

### 3. 스마트 SQL 트레이싱
- **완성형 SQL**: `?` 대신 실제 파라미터가 채워진 쿼리를 로그에 출력합니다.
- **SQL 가공**: 로그 복사 시 쿼리가 깨지는 것을 방지하기 위해 주석 제거 및 한 줄 최적화(DBeaver 복사-붙여넣기 최적화).
- **슬로우 쿼리 감지**: 설정된 시간(`ms`)을 초과하는 쿼리는 `[SLOW_SQL]` 로그로 자동 기록됩니다.

### 4. Grafana / Alloy 최적화
- 모든 로그는 `[API_PROD]`, `[BATCH_PROD]`, `[SQL]` 등 정형화된 **LogMarker**를 사용하여 Grafana UI에서 필드 추출 및 필터링이 용이합니다.
- `key=value` 포맷의 한 줄 로깅을 지향합니다.
- `traceId`를 통해 단일 요청의 전체 Lifecycle을 한눈에 추적할 수 있습니다.

---

## ⚙️ 설정 방법

### 1. application.properties 설정
```properties
# ==========================
# Logging Core
# ==========================

# 로그 추적 활성화 (기본값: true)
log.enabled=true 
# 로그 레벨 설정 (PROD, DEBUG, TRACE | 기본값: PROD)
log.trace.level=PROD

# ==========================
# Slow Query Threshold
# ==========================

# 개별 쿼리 기준 (ms)
log.slow.query.ms=300
# 한 API 내 전체 SQL 합계 기준 (ms)
log.slow.query.total-ms=1000

# ==========================
# SQL Limit
# ==========================

# 한 요청당 수집할 최대 SQL 개수 (기본값: 100)
log.limit.max-sql-count=100
# 수집할 SQL 문장의 최대 길이 (기본값: 2000)
log.limit.max-sql-length=2000
# 수집할 SQL 파라미터의 최대 길이 (기본값: 1000)
log.limit.max-sql-param-length=1000
```

### 2. 환경별 적용 방법

#### Spring MVC (Web)
- 자동으로 등록되므로 별도 설정이 필요 없습니다.

#### Netty TCP Server
- `ChannelPipeline` 구성 시 `MessageHandler` 바로 앞에 `NettyTraceDuplexHandler`를 추가합니다.
```java
pipeline.addLast(new NettyTraceDuplexHandler(loggingProperties, NettyServer.REAL_IP));
```

#### Spring Batch
- Job 빌드 시 `LoggingBatchListener`를 리스너로 등록합니다.
```java
return new JobBuilder("myJob", jobRepository)
        .listener(loggingBatchListener)
        .start(myStep)
        .build();
```

---

## 📦 Logback 설정 (`logback-spring.xml`)
Starter 로그를 올바르게 출력하기 위해 소비 프로젝트의 패턴에 **`[%X{traceId}]`**가 반드시 포함되어야 합니다.

```xml
<property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss:SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n" />
```

---

## ⚠️ 필수 주의 사항

1. **IFID 헤더**: 모든 HTTP 요청은 헤더에 `IFID`를 포함해야 API 요약 로그에 정상적으로 기록됩니다.
2. **JSON 에러 코드**: 응답 바디 내 `resCode`, `res_cd`, `code` 필드 값이 `9999`인 경우 자동으로 ERROR 수준의 상세 로그를 남깁니다.
3. **MyBatis 수동 설정 시**: `SqlSessionFactoryBean`을 직접 등록하는 경우 반드시 `SqlTraceInterceptor` 플러그인을 추가해야 합니다.

---


## 배포방법

**Git Push** 
```
git commit -m "Commit"
git push
``` 


**태그 생성**
```
git tag v1.0.x
```

**jitpack 배포**
```
git push origin v1.0.x
```