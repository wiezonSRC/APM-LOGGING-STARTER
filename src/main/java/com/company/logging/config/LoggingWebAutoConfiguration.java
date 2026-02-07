package com.company.logging.config;

import com.company.logging.filter.LoggingFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 웹 애플리케이션 로깅 자동 설정 클래스입니다.
 * <p>
 * HTTP 요청/응답 로깅을 위한 {@link LoggingFilter}를 등록합니다.
 * "log.trace.enabled" 속성이 true(또는 없을 경우 기본값 true)일 때 활성화됩니다.
 */
@Configuration
@ConditionalOnWebApplication
@EnableConfigurationProperties(LoggingProperties.class)
public class LoggingWebAutoConfiguration {


    //log.trace.enabled=true
    /**
     * LoggingFilter를 등록하고 순서를 설정합니다.
     * 필터 순서는 Ordered.HIGHEST_PRECEDENCE + 10으로 설정하여,
     * 다른 필터들보다 비교적 앞단에서 동작하도록 하지만 최상위 시스템 필터보다는 뒤에 위치시킵니다.
     *
     * @param properties 로깅 설정 속성
     * @return LoggingFilter 등록 빈
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "log.trace",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public FilterRegistrationBean<LoggingFilter> loggingFilterRegistration(
            LoggingProperties properties
    ) {
        FilterRegistrationBean<LoggingFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new LoggingFilter(properties));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        bean.addUrlPatterns("/*");
        return bean;
    }

}
