package com.company.logging.netty.config;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.netty.handler.NettyTraceDuplexHandler;
import io.netty.channel.ChannelHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Netty 로깅 관련 자동 설정 클래스입니다.
 * 직접적인 빈 등록보다는 사용자가 NettyServer에서 생성하여 사용할 수 있도록 가이드합니다.
 */
@Configuration
@ConditionalOnClass(ChannelHandler.class)
@ConditionalOnProperty(
        prefix = "log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NettyLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NettyTraceDuplexHandler nettyTraceDuplexHandler(LoggingProperties properties){
        return new NettyTraceDuplexHandler(properties);
    }
}
