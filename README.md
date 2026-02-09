# 🚀 Logging Starter

솔루션 구축 및 운영 환경에서 **추적성(Traceability)**과 **생산성**을 극대화하기 위한 Spring Boot Auto Configuration 라이브러리입니다.

## 🌟 주요 특징

### 1. 계층형 로깅 구조 (Hierarchical Logging)
설정된 레벨에 따라 정보의 상세도를 지능적으로 조절하며 로그 중복을 방지합니다.
- **PROD**: `IFID`, API 요약, 요청/응답 본문(JSON) 출력.
- **DEBUG**: PROD 정보 + SQL ID, 파라미터, 개별 쿼리 수행 시간.
- **TRACE**: DEBUG 정보 + **실제 값이 바인딩된 완성형 SQL문**.
- **ERROR**: 레벨에 상관없이 에러 발생 시 해당 요청의 상세 정보(Body, SQL)를 자동으로 노출.

### 2. 스마트 SQL 트레이싱
- **완성형 SQL**: `?` 대신 실제 파라미터가 채워진 쿼리를 로그에 출력합니다.
- **SQL 가공**: 로그 복사 시 쿼리가 깨지는 것을 방지하기 위해 `--` 주석을 자동으로 제거하고 한 줄로 최적화합니다. (DBeaver 복사-붙여넣기 최적화)
- **슬로우 쿼리 감지**: 설정된 시간(`ms`)을 초과하는 쿼리는 `[SLOW_SQL]` WARN 로그로 자동 기록됩니다.

### 3. Grafana / Alloy 최적화
- 모든 로그는 `key="value"` 포맷의 한 줄 로깅을 지향하여 Grafana UI에서 필드 추출 및 복사가 용이합니다.
- `traceId`를 통해 단일 요청의 전체 Lifecycle을 한눈에 추적할 수 있습니다.

---

## ⚙️ 설정 방법

### 1. application.properties 설정
```properties
# 로그 추적 활성화 (기본값: true)
log.trace.enabled=true

# 로그 레벨 설정 (PROD, DEBUG, TRACE | 기본값: PROD)
log.trace.level=PROD

# 슬로우 쿼리 임계치 설정
# 개별 쿼리 기준 (ms)
log.slow.query.ms=300
# 한 API 내 전체 SQL 합계 기준 (ms)
log.slow.query.total-ms=1000

# 로그 파일 경로
logging.file.path=/var/log/app
```

### 2. 필수 Logback 설정 (`logback-spring.xml`)
Starter에서 생성하는 로그를 올바르게 출력하기 위해 소비 프로젝트의 `src/main/resources/logback-spring.xml`에 아래 설정을 반드시 추가해야 합니다. (특히 **`[%X{traceId}]`** 패턴 누락 주의)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- Property 정의 -->
    <springProperty name="LOG_PATH" scope="context" source="logging.file.path" defaultValue="Log/"/>
    <property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss:SSS} [%thread] %-5level [%X{traceId}] %logger{36} [%M] - %msg%n" />

    <!-- Console Appender -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- File Appender -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/app-%d{yyyy-MM-dd}_%i.log</fileNamePattern>
            <maxHistory>30</maxHistory>
            <maxFileSize>10MB</maxFileSize>
        </rollingPolicy>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- Root Logger -->
    <root level="INFO">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>

    <!-- "Log" 이름의 로거 설정을 통해 Starter 로그 제어 -->
    <logger name="Log" level="INFO" additivity="false">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </logger>

</configuration>
```

---

## 📦 JitPack 배포 및 사용 방법

### 1. 배포 방법
본 라이브러리는 Git Tag를 기반으로 JitPack을 통해 배포됩니다.
1. 소스 코드를 Push합니다.
2. 새로운 태그를 생성합니다: `git tag v1.0.0`
3. 태그를 Push합니다: `git push origin v1.0.0`
4. [JitPack.io](https://jitpack.io)에서 배포 상태를 확인합니다.

### 2. 소비 프로젝트 설정 (build.gradle)
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.YOUR_GITHUB_ID:logging-starter:v1.0.0'
}
```

---

## ⚠️ 필수 주의 사항

1. **IFID 헤더**: 모든 요청은 헤더에 `IFID`를 포함해야 API 요약 로그에 정상적으로 기록됩니다.
2. **Logback 설정**: `logback-spring.xml`에서 `MDC` 필드인 `%X{traceId}`를 로그 패턴에 반드시 포함해야 합니다.
3. **MyBatis 수동 설정 시**: `SqlSessionFactoryBean`을 직접 Bean으로 등록하는 프로젝트는 반드시 `SqlTraceInterceptor`를 플러그인으로 추가해야 합니다.
   ```java
   sqlSessionFactoryBean.setPlugins(new SqlTraceInterceptor());
   ```