package com.company.logging.tool.batch.config;

import com.company.logging.tool.batch.listener.LoggingBatchListener;
import com.company.logging.tool.core.config.LoggingProperties;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Batch 환경을 위한 로깅 자동 설정 클래스입니다.
 */
@Configuration
@ConditionalOnClass({JobExecutionListener.class, StepExecutionListener.class})
public class LoggingBatchAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "log.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoggingBatchListener loggingBatchListener(LoggingProperties properties) {
        return new LoggingBatchListener(properties);
    }
}
