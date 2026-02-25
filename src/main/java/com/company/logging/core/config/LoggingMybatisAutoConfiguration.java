package com.company.logging.core.config;
import com.company.logging.core.sql.SqlTraceInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;

/**
 * MyBatis SQL 추적 자동 설정 클래스입니다.
 * <p>
 * MyBatis 관련 클래스가 존재하고, "log.trace.sql-enabled" 속성이 true(또는 없을 경우 기본값 true)일 때 활성화됩니다.
 * SQL 실행을 가로채어 로그를 남기기 위한 인터셉터를 등록합니다.
 */
@Configuration
@ConditionalOnClass(
        name = {
                "org.apache.ibatis.session.Configuration",
                "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
        }
)
public class LoggingMybatisAutoConfiguration {


    /**
     * SqlTraceInterceptor 빈을 생성합니다.
     * 이 인터셉터는 SQL 실행 시간을 측정하고 실행된 SQL과 파라미터를 캡처하는 역할을 합니다.
     *
     * @return SqlTraceInterceptor 인스턴스
     */
    @Bean
    @ConditionalOnMissingBean
    public SqlTraceInterceptor sqlTraceInterceptor() {
        return new SqlTraceInterceptor();
    }

    /**
     * MyBatis 설정에 SqlTraceInterceptor를 등록합니다.
     * 이를 통해 실제 MyBatis 실행 시점에 인터셉터가 동작하도록 설정합니다.
     *
     * @param interceptor 등록할 SqlTraceInterceptor
     * @return ConfigurationCustomizer
     */
    @Bean
    public ConfigurationCustomizer sqlTraceConfigurationCustomizer(
            SqlTraceInterceptor interceptor
    ) {
        return configuration -> configuration.addInterceptor(interceptor);
    }
}