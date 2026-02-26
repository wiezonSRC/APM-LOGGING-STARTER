# IRHEA Logging Starter 발표 자료

## 1. 프로젝트 개요
### **배경 및 필요성**
* **운영 환경의 문제 해결:** 운영 중인 서비스에서 장애 발생 시, 실제 어떤 요청 값이 들어왔고 어떤 SQL이 실행되었는지 파악하기 어렵습니다.
* **로깅의 파편화:** 여러 개발자가 각자 남기는 로그 방식이 달라 일관된 분석이 불가능합니다.
* **성능 모니터링:** 특정 API나 쿼리가 왜 느린지 직관적으로 확인하기 위한 도구가 필요합니다.

### **솔루션**
* **Spring Boot Starter 라이브러리:** 의존성 추가와 간단한 설정만으로 즉시 적용 가능한 통합 로깅 솔루션을 제공합니다.

---

## 2. 핵심 기능 (Key Features)

### **① 전 구간 HTTP 요청/응답 본문 로깅**
* Request/Response의 Headers, Parameters 뿐만 아니라 **Body 데이터**까지 로깅합니다.
* 파일 업로드/다운로드와 같은 바이너리 데이터는 자동으로 감지하여 메모리 이슈를 방지합니다.

### **② MyBatis SQL 완벽 추적 (Full SQL Trace)**
* 단순히 MyBatis 로그를 보여주는 것이 아니라, **'?' 플레이스홀더에 실제 파라미터가 바인딩된 완성된 SQL**을 출력합니다.
* 각 SQL별 실행 시간을 ms 단위로 측정하여 제공합니다.

### **③ Trace ID 기반의 요청 흐름 추적**
* **MDC(Mapped Diagnostic Context)**를 사용하여 요청마다 고유한 Trace ID를 부여합니다.
* 하나의 API 요청에서 발생하는 모든 로그(HTTP, SQL)를 동일한 ID로 묶어 선형적으로 분석할 수 있습니다.

### **④ 슬로우 쿼리 및 성능 탐지**
* 설정된 임계값(ms)을 초과하는 개별 쿼리 또는 요청 내 전체 쿼리 시간을 감지하여 `WARN` 로그로 경고를 남깁니다.

### **⑤ 유연한 로그 레벨 제어**
* `PROD`, `TRACE` 2단계 레벨을 통해 운영 상황에 맞춰 로그 상세도를 조절할 수 있습니다.
* 특정 요청에 헤더(`X-Debug-Trace`)를 추가하여 실시간으로 상세 로그를 강제 활성화할 수 있습니다.

---

## 3. 기술 아키텍처 (Technical Architecture)

### **컴포넌트 구조**
1.  **LoggingFilter (Servlet Filter):**
    * `OncePerRequestFilter`를 상속받아 모든 HTTP 요청의 시작과 끝을 관리합니다.
    * MDC 초기화 및 TraceContext 관리를 담당합니다.
2.  **Wrappers (Request/Response Wrapper):**
    * 스트림을 한 번만 읽을 수 있는 서블릿의 제약을 극복하기 위해 본문을 캐싱하는 `HttpServletRequestWrapper`, `HttpServletResponseWrapper`를 구현했습니다.
3.  **SqlTraceInterceptor (MyBatis Interceptor):**
    * MyBatis의 `Executor`를 인터셉트하여 실행 시점의 SQL과 파라미터를 추출합니다.
4.  **Context Holder (ThreadLocal):**
    * `ThreadLocal`을 사용하여 동일 스레드 내에서 수집된 SQL 정보들을 요청 종료 시점까지 안전하게 보관합니다.

---

## 4. 기대 효과 (Impact)

* **장애 대응 시간(MTTR) 단축:** 유입된 데이터와 실행된 SQL을 즉시 확인함으로써 원인 파악 시간을 획기적으로 줄입니다.
* **코드 가독성 향상:** 비즈니스 로직 내부에 로깅 코드를 섞지 않고 설정만으로 공통 로깅을 처리하여 코드가 깔끔해집니다.
* **성능 최적화 가이드:** 슬로우 쿼리 로그를 통해 인덱스 튜닝이 필요한 지점을 빠르게 식별할 수 있습니다.

---

## 5. 향후 발전 방향
* **ELK Stack 연동:** 수집된 정형 로그를 Elasticsearch로 전송하여 시각화 대시보드 구축.
* **외부 API 추적:** RestTemplate, WebClient 등 외부 인터페이스 호출 로그 추적 확장.
* **보안 필터링:** 개인정보(비밀번호, 카드번호 등)를 자동으로 마스킹 처리하는 기능 추가.
