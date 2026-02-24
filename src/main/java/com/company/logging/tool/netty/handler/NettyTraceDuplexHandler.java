package com.company.logging.tool.netty.handler;

import com.company.logging.tool.netty.context.LogNettyContext;
import com.company.logging.tool.core.config.LoggingProperties;
import com.company.logging.tool.core.sql.SqlTraceContext;
import com.company.logging.tool.core.sql.SqlTraceContextHolder;
import com.company.logging.tool.core.trace.LogProcessor;
import com.company.logging.tool.core.trace.TraceContextHolder;
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

    private static final AttributeKey<Long> START_NANO_KEY = AttributeKey.valueOf("START_NANO");
    private static final AttributeKey<String> TRACE_ID_KEY = AttributeKey.valueOf("TRACE_ID");
    private static final AttributeKey<String> REQUEST_DATA_KEY = AttributeKey.valueOf("REQUEST_DATA");
    private static final AttributeKey<SqlTraceContext> SQL_CONTEXT_KEY = AttributeKey.valueOf("SQL_CONTEXT");

    public NettyTraceDuplexHandler(LoggingProperties properties, AttributeKey<String> realIpKey) {
        this.properties = properties;
        this.logProcessor = new LogProcessor(properties);
        this.realIpKey = realIpKey;
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx,@NonNull Object msg) throws Exception {
        long startNano = System.nanoTime();
        String traceId = UUID.randomUUID().toString();

        // 1. 컨텍스트 시작
        MDC.put("traceId", traceId);
        TraceContextHolder.init(properties.getTrace().getLevel(), false);
        SqlTraceContextHolder.init();

        // 2. 속성 저장 (동시성 안전을 위해 채널 속성 사용)
        ctx.channel().attr(START_NANO_KEY).set(startNano);
        ctx.channel().attr(TRACE_ID_KEY).set(traceId);
        ctx.channel().attr(REQUEST_DATA_KEY).set(safeToString(msg));
        ctx.channel().attr(SQL_CONTEXT_KEY).set(SqlTraceContextHolder.get());

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        String responseData = safeToString(msg);

        // write 완료 후 로깅 수행 (Chunked write 및 성공 여부 확인을 위해 Listener 사용)
        promise.addListener(future -> {
            try {
                if (future.isSuccess()) logNetty(ctx, responseData, null);
                else logNetty(ctx, responseData, future.cause() instanceof Exception e ? e : new RuntimeException(future.cause()));
            } finally {
                clearContext(ctx);
            }
        });

        super.write(ctx, msg, promise);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Exception ex;
        if (cause instanceof Exception exception) ex = exception;
        else ex = new RuntimeException(cause);

        try {
            logNetty(ctx, null, ex);
        } finally {
            clearContext(ctx);
        }
        super.exceptionCaught(ctx, cause);
    }

    private void logNetty(ChannelHandlerContext ctx, String resData, Exception ex) {
        String traceId = ctx.channel().attr(TRACE_ID_KEY).get();
        if (traceId == null) return;

        // MDC 복구 (Netty EventLoop 스레드에서 실행될 때 유효)
        MDC.put("traceId", traceId);

        Long startNano = ctx.channel().attr(START_NANO_KEY).get();
        double elapsedMs = (startNano != null) ? (System.nanoTime() - startNano) / 1_000_000.0 : 0;

        String clientIp = ctx.channel().attr(realIpKey).get();
        if (clientIp == null) {
            clientIp = ctx.channel().remoteAddress().toString();
        }

        String reqData = ctx.channel().attr(REQUEST_DATA_KEY).get();
        SqlTraceContext sqlCtx = ctx.channel().attr(SQL_CONTEXT_KEY).get();

        LogNettyContext.Builder builder = new LogNettyContext.Builder()
                .interfaceId("NETTY_TCP")
                .clientIp(clientIp)
                .method("TCP")
                .status(ex == null ? "OK" : "ERROR")
                .elapsedMs(elapsedMs)
                .requestData(reqData != null ? reqData : "")
                .responseData(resData != null ? resData : "")
                .ex(ex);

        if (sqlCtx != null) {
            builder.sqlCount(sqlCtx.count())
                   .sqlTotalElapsed(sqlCtx.getTotalElapsed());
        }

        logProcessor.logNetty(builder.build());
    }

    private void clearContext(ChannelHandlerContext ctx) {
        // 채널 속성 정리
        ctx.channel().attr(START_NANO_KEY).set(null);
        ctx.channel().attr(TRACE_ID_KEY).set(null);
        ctx.channel().attr(REQUEST_DATA_KEY).set(null);
        ctx.channel().attr(SQL_CONTEXT_KEY).set(null);

        // 스레드 로컬 정리
        SqlTraceContextHolder.clear();
        TraceContextHolder.clear();
        MDC.clear();
    }

    private String safeToString(Object msg) {
        if (msg == null) return "";
        if (msg instanceof String s) return s;
        if (msg instanceof io.netty.buffer.ByteBuf buf) {
            // ByteBuf의 내용을 읽되, 포인터는 유지
            return buf.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
        return msg.toString();
    }
}