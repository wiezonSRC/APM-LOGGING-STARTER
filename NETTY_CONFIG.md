# Netty (Asynchronous API) Logging Configuration

Netty 기반 고성능 API 서버(TX 등)를 위한 로깅 설정 가이드입니다.

## 1. 기본 설정 및 핸들러 활성화
```properties
# 로깅 기능 전체 활성화
log.enabled=true
```
*주의: Netty 환경에서는 Pipeline에 `NettyTraceDuplexHandler`가 등록되어 있어야 합니다.*

## 2. Netty 전용 로깅 전략
```properties
# 추적 레벨 (PROD, TRACE)
log.trace.level=PROD

# API 응답 지연 임계값 (1000ms 초과 시 슬로우 로그 기록)
log.slow.api-ms=1000

# Netty ByteBuf 캡처 모드
log.capture.body=ERROR
```

## 3. SQL 및 리소스 추적
```properties
# 비동기 처리 중 발생하는 SQL 로깅
log.capture.sql=SLOW

# 한 세션당 최대 추적 SQL 개수 (기본 100개)
log.limit.max-sql-count=50
```

## 4. 제약 사항 설정 (고성능 유지용)
```properties
# 로그 기록에 의한 성능 저하를 방지하기 위해 낮은 길이 제한 권장
log.limit.max-body-length=1000
log.limit.max-sql-length=1000
```
