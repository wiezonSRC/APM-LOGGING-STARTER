# Servlet (Spring MVC) Logging Configuration

Servlet 기반 웹 애플리케이션(IMS, MMS 등)을 위한 로깅 설정 가이드입니다.

## 1. 기본 활성화
```properties
# 로깅 기능 전체 활성화 (기본값: true)
log.enabled=true
```

## 2. API 추적 정책
```properties
# 추적 레벨 (PROD, TRACE 중 선택)
# PROD: 일반적인 운영 로그
# TRACE: 모든 상세 정보를 포함하는 개발/디버깅용 로그
log.trace.level=PROD

# HTTP Body 캡처 모드 (ALWAYS, ERROR, SLOW, SAMPLE, OFF)
# ERROR: 에러 발생 시에만 Body 기록 (권장)
log.capture.body=ERROR

# 확률 기반 샘플링 (1%의 요청만 상세 로깅)
log.capture.sample-rate=0.01
```

## 3. SQL 로깅 정책
```properties
# SQL 캡처 모드 (ALWAYS, ERROR, SLOW, SAMPLE, OFF)
log.capture.sql=SLOW

# 슬로우 쿼리 기준 (300ms 초과 시 기록)
log.slow.query.ms=300
```

## 4. 길이 및 개수 제한 (OOM 방지)
```properties
# Request/Response Body 최대 기록 길이 (기본 2000자)
log.limit.max-body-length=2000

# 한 요청당 기록할 최대 SQL 개수
log.limit.max-sql-count=100

# 에러 로그 시 StackTrace 출력 깊이 제한
log.limit.max-stack-depth=5
```
