package com.company.logging.tool.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(LoggingProperties.class)
public class LoggingCoreAutoConfiguration {
}
