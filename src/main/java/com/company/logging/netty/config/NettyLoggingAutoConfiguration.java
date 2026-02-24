package com.company.logging.netty.config;

import io.netty.channel.ChannelHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * Netty 로깅 관련 자동 설정 클래스입니다.
 * 직접적인 빈 등록보다는 사용자가 NettyServer에서 생성하여 사용할 수 있도록 가이드합니다.
 */
@Configuration
@ConditionalOnClass(ChannelHandler.class)
public class NettyLoggingAutoConfiguration {
    // 필요한 경우 공통 설정을 여기에 추가할 수 있습니다.
}
