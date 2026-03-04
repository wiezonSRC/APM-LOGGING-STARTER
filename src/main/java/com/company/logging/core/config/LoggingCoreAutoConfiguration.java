package com.company.logging.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

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
}
