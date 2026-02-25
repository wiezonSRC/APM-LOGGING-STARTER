package com.company.logging.netty.handler;

import com.company.logging.netty.context.LogNettyContext;
import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.sql.SqlTraceContext;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.context.TraceContextHolder;
import com.company.logging.netty.process.NettyLogProcessor;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import org.springframework.lang.NonNull;
import com.company.logging.core.support.util.TraceIdUtil;

/**
 * Netty TCP 환경에서 요청/응답 객체 및 SQL 실행 정보를 통합 로깅하는 듀플렉스 핸들러입니다.
 */
public class NettyTraceDuplexHandler extends ChannelDuplexHandler {

    private final NettyLogProcessor logProcessor;
    private final LoggingProperties properties;

    private static final AttributeKey<String> REAL_IP_KEY = AttributeKey.valueOf("REAL_IP");
    private static final AttributeKey<Long> START_NANO_KEY = AttributeKey.valueOf("START_NANO");
    private static final AttributeKey<String> TRACE_ID_KEY = AttributeKey.valueOf("TRACE_ID");
    private static final AttributeKey<String> SPAN_ID_KEY = AttributeKey.valueOf("SPAN_ID");
    private static final AttributeKey<String> REQUEST_DATA_KEY = AttributeKey.valueOf("REQUEST_DATA");
    private static final AttributeKey<SqlTraceContext> SQL_CONTEXT_KEY = AttributeKey.valueOf("SQL_CONTEXT");

    public NettyTraceDuplexHandler(LoggingProperties properties) {
        this.properties = properties;
        this.logProcessor = new NettyLogProcessor(properties);
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx,@NonNull Object msg) throws Exception {
        long startNano = System.nanoTime();
        String traceId = TraceIdUtil.generateTraceId();
        String spanId = TraceIdUtil.generateSpanId();

        TraceContextHolder.init(traceId, spanId, properties.getTrace().getLevel(), false);
        SqlTraceContextHolder.init();

        ctx.channel().attr(START_NANO_KEY).set(startNano);
        ctx.channel().attr(TRACE_ID_KEY).set(traceId);
        ctx.channel().attr(SPAN_ID_KEY).set(spanId);
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
        String spanId = ctx.channel().attr(SPAN_ID_KEY).get();
        if (traceId == null) return;

        Long startNano = ctx.channel().attr(START_NANO_KEY).get();
        double elapsedMs = (startNano != null) ? (System.nanoTime() - startNano) / 1_000_000.0 : 0;

        String clientIp = ctx.channel().attr(REAL_IP_KEY).get();
        if (clientIp == null) {
            clientIp = ctx.channel().remoteAddress().toString();
        }

        String reqData = ctx.channel().attr(REQUEST_DATA_KEY).get();
        SqlTraceContext sqlCtx = ctx.channel().attr(SQL_CONTEXT_KEY).get();

        LogNettyContext.Builder builder = new LogNettyContext.Builder()
                .traceId(traceId)
                .spanId(spanId)
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

        logProcessor.logApi(builder.build());
    }

    private void clearContext(ChannelHandlerContext ctx) {
        // 채널 속성 정리
        ctx.channel().attr(START_NANO_KEY).set(null);
        ctx.channel().attr(TRACE_ID_KEY).set(null);
        ctx.channel().attr(SPAN_ID_KEY).set(null);
        ctx.channel().attr(REQUEST_DATA_KEY).set(null);
        ctx.channel().attr(SQL_CONTEXT_KEY).set(null);

        // 스레드 로컬 정리
        SqlTraceContextHolder.clear();
        TraceContextHolder.clear();
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