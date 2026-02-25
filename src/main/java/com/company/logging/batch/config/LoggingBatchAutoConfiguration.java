package com.company.logging.batch.config;

import com.company.logging.batch.listener.LoggingBatchListener;
import com.company.logging.core.config.LoggingProperties;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Batch 환경을 위한 로깅 자동 설정 클래스입니다.
 */
@Configuration
@ConditionalOnClass({JobExecutionListener.class, StepExecutionListener.class})
@ConditionalOnProperty(
        prefix = "log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LoggingBatchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LoggingBatchListener loggingBatchListener(LoggingProperties properties) {
        return new LoggingBatchListener(properties);
    }
}
