package com.company.logging.servlet.config;

import com.company.logging.core.async.AsyncLogEventQueue;
import com.company.logging.core.config.LoggingProperties;
import com.company.logging.servlet.filter.LoggingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;

/**
 * 웹 애플리케이션 로깅 자동 설정 클래스입니다.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LoggingWebAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "log.async", name = "enabled", havingValue = "true")
    public AsyncLogEventQueue asyncLogEventQueue(LoggingProperties properties) {
        return new AsyncLogEventQueue(properties.getAsync());
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<LoggingFilter> loggingFilterRegistration(
            LoggingProperties properties,
            @Autowired(required = false) @Nullable AsyncLogEventQueue asyncQueue
    ) {
        FilterRegistrationBean<LoggingFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new LoggingFilter(properties, asyncQueue));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
