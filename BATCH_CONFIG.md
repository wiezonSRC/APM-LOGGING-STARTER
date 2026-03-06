# Spring Batch Logging Configuration

대량 데이터 처리를 수행하는 Batch 애플리케이션을 위한 로깅 설정 가이드입니다.

## 1. 기본 활성화 및 리스너
```properties
# 배치 로깅 전체 활성화
log.enabled=true
```
*주의: 배치 Job/Step에 `LoggingBatchListener`가 등록되어 있어야 요약 정보가 기록됩니다.*

## 2. 배치 특화 SQL 설정
배치는 한 Step 내에서 수만 건의 SQL이 발생할 수 있습니다. 메모리 보호를 위해 개수 제한을 조정하십시오.

```properties
# 모든 SQL을 추적할지, 슬로우/에러만 추적할지 결정
log.capture.sql=ALWAYS

# 한 Step 내에서 추적(기억)할 최대 SQL 개수 (기본 100개)
# 이 수치를 넘어가면 "(OMITTED)" 로그와 함께 개수만 요약됩니다.
log.limit.max-sql-count=500

# 로그 파일에 상세 SQL 문장과 파라미터를 남길 개수 (기본 10개)
# 이 수치를 넘어가면 실행 시간만 기록되어 로그 파일 크기를 줄입니다.
log.limit.max-sql-detail-count=50
```

## 3. 실행 속도 분석 설정
```properties
# 배치 Step 전체 실행 시간이 1000ms를 넘으면 요약 로그 기록
log.slow.api-ms=1000

# 개별 쿼리 슬로우 임계값 (배치는 성능이 중요하므로 낮게 설정 권장)
log.slow.query.ms=100
```

## 4. 비동기 처리(멀티스레드 배치) 필수 설정
비동기 Step(`taskExecutor` 사용) 시에는 다음 Java 설정이 수반되어야 합니다.

```java
// 예시: TaskExecutor 설정 시 Decorator 추가 필수
executor.setTaskDecorator(new LoggingTaskDecorator());
```
(이 설정이 없으면 비동기 쓰레드에서 발생하는 SQL 로그가 누락됩니다.)
