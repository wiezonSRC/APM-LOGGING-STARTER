package com.company.logging.core.config;

import com.company.logging.core.aop.ExternalCallTracingAspect;
import com.company.logging.core.metrics.InMemoryMetricsCollector;
import com.company.logging.core.metrics.MetricsHolder;
import com.company.logging.core.metrics.MetricsSnapshotLogger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(LoggingProperties.class)
@ConditionalOnProperty(
        prefix = "log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LoggingCoreAutoConfiguration {

    public LoggingCoreAutoConfiguration(LoggingProperties properties) {
        LoggingPropertiesHolder.setProperties(properties);
    }

    /**
     * 인메모리 메트릭 수집기를 빈으로 등록하고 StaticHolder에 세팅합니다.
     * 컨슈머가 별도 MetricsCollector 빈을 등록하면 자동으로 비활성화됩니다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "log", name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
    public InMemoryMetricsCollector inMemoryMetricsCollector() {
        InMemoryMetricsCollector collector = new InMemoryMetricsCollector();
        MetricsHolder.set(collector);
        return collector;
    }

    /**
     * 60초 주기로 메트릭 스냅샷을 로그로 출력하는 스케줄러 빈입니다.
     * @EnableScheduling 없이 독립 데몬 스레드로 동작합니다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "log", name = "metrics.enabled", havingValue = "true", matchIfMissing = true)
    public MetricsSnapshotLogger metricsSnapshotLogger(InMemoryMetricsCollector collector) {
        return new MetricsSnapshotLogger(collector);
    }

    /**
     * RestTemplate 외부 호출 추적 Aspect입니다.
     * log.trace.external-call=true 설정 및 spring-aop 의존성이 있을 때만 활성화됩니다.
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnProperty(prefix = "log.trace", name = "external-call", havingValue = "true")
    public ExternalCallTracingAspect externalCallTracingAspect() {
        return new ExternalCallTracingAspect();
    }
}
