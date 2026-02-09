# logging-starter Logback 설정 가이드

본 문서는 `logging-starter` 라이브러리를 사용하는 **소비 프로젝트에서 반드시 설정해야 하는 Logback 구성**을 설명한다.  
Starter는 로그를 **생성**만 하며, 로그의 **출력 정책(레벨/파일/경로)** 은 소비 프로젝트에서 관리한다.

---

## 1. 개요

`logging-starter`는 다음과 같은 로그 레벨 구조를 전제로 동작한다.

| 구분 | 설명 |
|---|---|
| API_PROD | 운영 기본 로그 (항상 출력) |
| API_DEBUG | 디버그용 로그 |
| API_TRACE | 요청/응답/SQL 전체 추적 |
| SQL_TRACE | SQL 실행 정보 (INFO 이상) |
| SQL_DEBUG | SQL 상세 디버그 (선택적) |

---

## 2. 필수 Logback 설정 (`logback-spring.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_TEMP" value="Log/" />

    <!-- Property 정의 -->
    <springProperty name="LOG_PATH" scope="context" source="logging.file.path" defaultValue="/"/>
    <property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss:SSS} [%thread] %-5level [%X{traceId}] %logger{36} [%M] - [IMS] %msg%n" />

    <!-- [Conslot] -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <!-- [MAIN FILE] -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/IMS.log</file>
        <prudent>false</prudent>

        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
            <immediateFlush>true</immediateFlush>
        </encoder>


        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/IMS-%d{yyyy-MM-dd}_%i.log</fileNamePattern>
            <maxHistory>30</maxHistory>
            <maxFileSize>10MB</maxFileSize>
        </rollingPolicy>
    </appender>

    <appender name="SQL_DEBUG_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/SQL_DEBUG.log</file>
        <prudent>false</prudent>
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
            <charset>UTF-8</charset>
            <immediateFlush>true</immediateFlush>
        </encoder>

        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/SQL_DEBUG-%d{yyyy-MM-dd}_%i.log</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>14</maxHistory>
        </rollingPolicy>
    </appender>


    <!-- === [ROOT LOGGER] === -->
    <root level="INFO" >
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>

    <!-- === [SQL_TRACE (INFO, WARN, ERROR) ] === -->
    <logger name="SQL_TRACE" level="INFO" additivity="false">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE"/>
    </logger>

    <!-- === [SQL DEBUG] === -->
    <logger name="SQL_DEBUG" level="OFF" additivity="false">
        <appender-ref ref="SQL_DEBUG_FILE"/>
    </logger>

    <!--  =================================
                    log4jdbc OFF
          ================================= -->

    <logger name="jdbc" level="OFF" />
    <logger name="jdbc.sqlonly" level="OFF" />
    <logger name="jdbc.sqltiming" level="OFF" />
    <logger name="jdbc.audit" level="OFF" />
    <logger name="jdbc.resultset" level="OFF" />
    <logger name="jdbc.resultsettable" level="OFF" />
    <logger name="jdbc.connection" level="OFF" />

</configuration>
```

---

## 3. application.properties 예시

```properties
#default (true)
log.trace.enabled=true
# PROD<DEBUG<TRACE | default (prod) 
log.trace.level=PROD
# 쿼리 하나당 걸린 시간 | default(300)
log.slow.query.ms=300
# 하나의 flow의 총 걸린 sql 시간 | default(1000)
log.slow.query.total-ms=1000

logging.file.path=/var/log/ims
```


---
## ⚠️ SqlSessionFactoryBean 수동 등록 시 필수 설정 안내

본 프로젝트는 SQL Trace 로그 수집을 위해 MyBatis Interceptor(`SqlTraceInterceptor`)를 사용합니다.

Spring Boot 자동 설정을 사용하지 않고,  
`SqlSessionFactoryBean`을 **수동으로 생성하여 등록하는 경우**  
MyBatis Interceptor는 자동으로 적용되지 않습니다.

따라서 아래와 같이 `SqlTraceInterceptor`를 **반드시 직접 설정해야 합니다**.

```java
SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
Interceptor[] sqlInterceptor = { new SqlTraceInterceptor() };
sqlSessionFactoryBean.setPlugins(sqlInterceptor);
```

---
## 4. 주의 사항

- 반드시 `logback.xml` 사용
- Mybatis 수동설정시, setPlugin으로 interceptor 추가 필요 (SqlTraceInteceptor)
- Logger 이름 변경 금지
- `%X{traceId}` 제거 금지


---
## 5. 배포 방법
```
1. 태그 생성

-> git tag v1.0.x

2. 태그 push

-> git push origin v1.0.x 
```


