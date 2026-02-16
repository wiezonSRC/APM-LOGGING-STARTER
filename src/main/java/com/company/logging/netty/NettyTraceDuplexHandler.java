package com.company.logging.netty;

import com.company.logging.context.LogNettyContext;
import com.company.logging.config.LoggingProperties;
import com.company.logging.sql.SqlTraceContextHolder;
import com.company.logging.trace.LogProcessor;
import com.company.logging.trace.TraceContextHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;

import java.util.UUID;

/**
 * Netty TCP 환경에서 요청/응답 객체 및 SQL 실행 정보를 통합 로깅하는 듀플렉스 핸들러입니다.
 */
public class NettyTraceDuplexHandler extends ChannelDuplexHandler {

    private final LogProcessor logProcessor;
    private final LoggingProperties properties;
    private final AttributeKey<String> realIpKey;
    
    private long startNano;
    private Object requestMsg;

    public NettyTraceDuplexHandler(LoggingProperties properties, AttributeKey<String> realIpKey) {
        this.properties = properties;
        this.logProcessor = new LogProcessor(properties);
        this.realIpKey = realIpKey;
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx,@NonNull Object msg) throws Exception {
        this.startNano = System.nanoTime();
        this.requestMsg = msg;

        // 1. 컨텍스트 시작
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        TraceContextHolder.init(properties.getTrace().getLevel(), false);
        SqlTraceContextHolder.init();

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            // 2. 통합 로깅 수행 (객체 상태의 msg를 로깅)
            logNetty(ctx, requestMsg, msg, null);
        } finally {
            // 3. 컨텍스트 정리 후 다음 핸들러(인코더)로 전달
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof Exception exception) logNetty(ctx, requestMsg, null, exception);
        else logNetty(ctx, requestMsg, null, new RuntimeException(cause));
        super.exceptionCaught(ctx, cause);
    }

    private void logNetty(ChannelHandlerContext ctx, Object req, Object res, Exception ex) {
        if (MDC.get("traceId") == null) return;

        double elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;
        String clientIp = ctx.channel().attr(realIpKey).get();
        if (clientIp == null) {
            clientIp = ctx.channel().remoteAddress().toString();
        }

        LogNettyContext nettyContext = new LogNettyContext.Builder()
                .interfaceId("NETTY_TCP")
                .clientIp(clientIp)
                .method("TCP")
                .status(ex == null ? "OK" : "ERROR")
                .elapsedMs(elapsedMs)
                .requestData(req != null ? req.toString() : "")
                .responseData(res != null ? res.toString() : "")
                .ex(ex)
                .build();

        logProcessor.logNetty(nettyContext);

        SqlTraceContextHolder.clear();
        TraceContextHolder.clear();
        MDC.clear();
    }
}