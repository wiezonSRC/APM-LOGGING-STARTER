package com.company.logging.netty.handler;

import com.company.logging.core.support.util.CommonUtil;
import com.company.logging.netty.context.LogNettyContext;
import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.sql.SqlTraceContext;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.context.TraceContextHolder;
import com.company.logging.netty.process.NettyLogProcessor;
import com.company.logging.core.support.sql.SQLUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import org.springframework.lang.NonNull;
import com.company.logging.core.support.util.TraceIdUtil;

/**
 * Netty TCP 환경에서 요청/응답 객체 및 SQL 실행 정보를 통합 로깅하는 듀플렉스 핸들러입니다.
 */
@ChannelHandler.Sharable
public class NettyTraceDuplexHandler extends ChannelDuplexHandler {

    private final NettyLogProcessor logProcessor;
    private final LoggingProperties properties;

    private static final AttributeKey<String> REAL_IP_KEY = AttributeKey.valueOf("REAL_IP");
    private static final AttributeKey<Long> START_NANO_KEY = AttributeKey.valueOf("START_NANO");
    private static final AttributeKey<String> TRACE_ID_KEY = AttributeKey.valueOf("TRACE_ID");
    private static final AttributeKey<String> SPAN_ID_KEY = AttributeKey.valueOf("SPAN_ID");
    private static final AttributeKey<String> REQUEST_DATA_KEY = AttributeKey.valueOf("REQUEST_DATA");
    private static final AttributeKey<String> RESPONSE_DATA_KEY = AttributeKey.valueOf("RESPONSE_DATA");
    private static final AttributeKey<SqlTraceContext> SQL_CONTEXT_KEY = AttributeKey.valueOf("SQL_CONTEXT");

    public NettyTraceDuplexHandler(LoggingProperties properties) {
        this.properties = properties;
        this.logProcessor = new NettyLogProcessor(properties);
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx,@NonNull Object msg) throws Exception {
        String traceId = ctx.channel().attr(TRACE_ID_KEY).get();
        String spanId = ctx.channel().attr(SPAN_ID_KEY).get();

        if (traceId == null) {
            traceId = TraceIdUtil.generateTraceId();
            spanId = TraceIdUtil.generateSpanId();
            ctx.channel().attr(TRACE_ID_KEY).set(traceId);
            ctx.channel().attr(SPAN_ID_KEY).set(spanId);
            ctx.channel().attr(START_NANO_KEY).set(System.nanoTime());
            ctx.channel().attr(REQUEST_DATA_KEY).set("");
            ctx.channel().attr(RESPONSE_DATA_KEY).set("");
            
            SqlTraceContext sqlCtx = SqlTraceContextHolder.init();
            ctx.channel().attr(SQL_CONTEXT_KEY).set(sqlCtx);
        }

        // 각 Chunk 처리 시 ThreadLocal 복구
        TraceContextHolder.init(traceId, spanId, properties.getTrace().getLevel(), false);
        SqlTraceContextHolder.set(ctx.channel().attr(SQL_CONTEXT_KEY).get());

        // Request 데이터 누적 및 Truncate
        String currentReq = ctx.channel().attr(REQUEST_DATA_KEY).get();
        ctx.channel().attr(REQUEST_DATA_KEY).set(accumulate(currentReq, safeToString(msg)));

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        String data = safeToString(msg);
        String currentRes = ctx.channel().attr(RESPONSE_DATA_KEY).get();
        String accumulatedRes = accumulate(currentRes, data);
        ctx.channel().attr(RESPONSE_DATA_KEY).set(accumulatedRes);

        // write 완료 후 로깅 수행
        promise.addListener(future -> {
            try {
                if (future.isSuccess()) {
                    logNetty(ctx, null);
                } else {
                    logNetty(ctx, future.cause() instanceof Exception e ? e : new RuntimeException(future.cause()));
                }
            } finally {
                // 한 번의 write가 전체 응답 완료라고 가정하기 어려울 수 있으나, 
                // 일반적인 경우 writeListener에서 정리
                clearContext(ctx);
            }
        });

        super.write(ctx, msg, promise);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            clearContext(ctx);
        } finally {
            super.channelInactive(ctx);
        }
    }

    private void logNetty(ChannelHandlerContext ctx, Exception ex) {
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
        String resData = ctx.channel().attr(RESPONSE_DATA_KEY).get();
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
        ctx.channel().attr(START_NANO_KEY).set(null);
        ctx.channel().attr(TRACE_ID_KEY).set(null);
        ctx.channel().attr(SPAN_ID_KEY).set(null);
        ctx.channel().attr(REQUEST_DATA_KEY).set(null);
        ctx.channel().attr(RESPONSE_DATA_KEY).set(null);
        ctx.channel().attr(SQL_CONTEXT_KEY).set(null);

        SqlTraceContextHolder.clear();
        TraceContextHolder.clear();
    }

    private String accumulate(String current, String addition) {
        if (addition == null || addition.isEmpty()) return current;
        if (current == null) current = "";
        
        int max = properties.getLimit().getMaxBodyLength();
        if (current.length() >= max) return current;

        return CommonUtil.truncate(current + addition, max);
    }

    private String safeToString(Object msg) {
        if (msg == null) return "";
        if (msg instanceof String s) return s;
        if (msg instanceof io.netty.buffer.ByteBuf buf) {
            int max = properties.getLimit().getMaxBodyLength();
            int readableBytes = buf.readableBytes();
            if (readableBytes == 0) return "";
            
            int lengthToRead = Math.min(readableBytes, max);
            String result = buf.toString(buf.readerIndex(), lengthToRead, java.nio.charset.StandardCharsets.UTF_8);
            return readableBytes > max ? result + "...(TRUNCATED)" : result;
        }
        return msg.toString();
    }
}