package com.company.logging.core.config;

/**
 * LoggingProperties를 정적으로 관리하여 Spring Context 외부나
 * 수동으로 객체를 생성하는 경우에도 설정을 참조할 수 있게 도와주는 홀더 클래스입니다.
 */
public class LoggingPropertiesHolder {
    private static LoggingProperties properties;

    public static void setProperties(LoggingProperties properties) {
        LoggingPropertiesHolder.properties = properties;
    }

    public static LoggingProperties getProperties() {
        return properties;
    }
}
