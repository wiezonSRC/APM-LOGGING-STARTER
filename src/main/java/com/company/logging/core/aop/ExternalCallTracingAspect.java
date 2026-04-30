package com.company.logging.core.aop;

import com.company.logging.core.context.TraceContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * RestTemplate 외부 HTTP 호출을 가로채어 Breadcrumb를 기록하는 AOP Aspect입니다.
 *
 * <p>log.trace.external-call=true 설정 시에만 활성화되며,
 * 비즈니스 로직에 영향을 주지 않는 Fail-safe 구조로 동작합니다.</p>
 *
 * <p>포인트컷 대상:
 * <ul>
 *   <li>RestTemplate: execute(), exchange(), getForObject() 등 모든 퍼블릭 메서드</li>
 * </ul>
 * </p>
 */
@Aspect
public class ExternalCallTracingAspect {

    private static final Logger logger = LoggerFactory.getLogger("Log");

    @Around("execution(* org.springframework.web.client.RestTemplate.*(..))")
    public Object traceRestTemplate(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        boolean isError = false;

        try {
            return pjp.proceed();
        } catch (Throwable t) {
            isError = true;
            throw t;
        } finally {
            try {
                long elapsed = System.currentTimeMillis() - start;
                String methodName = pjp.getSignature().getName();
                String detail = resolveDetail(pjp, methodName, elapsed, isError);

                TraceContextHolder.addBreadcrumb(
                    isError ? "EXTERNAL_ERROR" : "EXTERNAL_CALL",
                    detail
                );
            } catch (Exception ignored) {
                // Breadcrumb 기록 실패가 외부 호출 결과에 영향을 주어서는 안 됨
            }
        }
    }

    private String resolveDetail(ProceedingJoinPoint pjp, String methodName, long elapsed, boolean isError) {
        Object[] args = pjp.getArgs();

        // exchange(URI, HttpMethod, ...) 또는 exchange(String, ...) 형태에서 URL 추출
        String url = extractUrl(args);

        if (url != null) {
            return "RestTemplate." + methodName + " " + url + " " + elapsed + "ms" + (isError ? " ERROR" : "");
        }

        return "RestTemplate." + methodName + " " + elapsed + "ms" + (isError ? " ERROR" : "");
    }

    private String extractUrl(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            if (arg instanceof String s && (s.startsWith("http://") || s.startsWith("https://"))) {
                return s;
            }

            if (arg instanceof java.net.URI uri) {
                return uri.toString();
            }

            if (arg instanceof HttpRequest req) {
                return req.getURI().toString();
            }
        }

        return null;
    }
}
