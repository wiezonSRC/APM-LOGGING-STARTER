package com.company.logging.netty.handler;

import com.company.logging.core.support.util.CommonUtil;
import com.company.logging.netty.context.LogNettyContext;
import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.sql.SqlTraceContext;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.context.TraceContextHolder;
import com.company.logging.netty.process.NettyLogProcessor;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import org.springframework.lang.NonNull;
import com.company.logging.core.support.util.SamplingDecider;
import com.company.logging.core.support.util.TraceIdUtil;

/**
 * Netty TCP 환경에서 요청/응답 객체 및 SQL 실행 정보를 통합 로깅하는 듀플렉스 핸들러입니다.
 * <p>
 * <b>권장 사용법:</b>
 * 모든 인바운드/아웃바운드 이벤트와 예외를 완벽하게 캡처하기 위해 파이프라인의 시작과 끝에 동일한 인스턴스를 추가하는 것을 권장합니다.
 * </p>
 * <pre>
 *   pipeline.addFirst("logging_head", nettyTraceDuplexHandler);
 *   // ... (Decoders, Encoders, Application Handlers) ...
 *   pipeline.addLast("logging_tail", nettyTraceDuplexHandler);
 * </pre>
 * <p>
 * head 인스턴스는 TraceId 초기화, 요청 바디 수집, 최종 응답 로깅을 담당하며,
 * tail 인스턴스는 애플리케이션 핸들러에서 발생한 예외를 캡처하여 로깅합니다.
 * 중복 누적 및 로깅은 내부적으로 방지됩니다.
 * </p>
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
    private static final AttributeKey<Exception> ERROR_CONTEXT_KEY = AttributeKey.valueOf("ERROR_CONTEXT");
    private static final AttributeKey<Boolean> FORCE_TRACE_KEY = AttributeKey.valueOf("FORCE_TRACE");

    // 중복 누적 방지를 위한 Attribute
    private static final AttributeKey<Integer> LAST_INBOUND_ID = AttributeKey.valueOf("LAST_INBOUND_ID");
    private static final AttributeKey<Integer> LAST_OUTBOUND_ID = AttributeKey.valueOf("LAST_OUTBOUND_ID");

    public NettyTraceDuplexHandler(LoggingProperties properties) {
        this.properties = properties;
        this.logProcessor = new NettyLogProcessor(properties);
    }

    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) throws Exception {
        String traceId = ctx.channel().attr(TRACE_ID_KEY).get();
        String spanId = ctx.channel().attr(SPAN_ID_KEY).get();
        Boolean forceTrace = ctx.channel().attr(FORCE_TRACE_KEY).get();

        if (traceId == null) {
            traceId = TraceIdUtil.generateTraceId();
            spanId = TraceIdUtil.generateSpanId();

            // 샘플링 결정
            forceTrace = SamplingDecider.shouldForceTrace(properties, false);

            ctx.channel().attr(TRACE_ID_KEY).set(traceId);
            ctx.channel().attr(SPAN_ID_KEY).set(spanId);
            ctx.channel().attr(FORCE_TRACE_KEY).set(forceTrace);
            ctx.channel().attr(START_NANO_KEY).set(System.nanoTime());
            ctx.channel().attr(REQUEST_DATA_KEY).set("");
            ctx.channel().attr(RESPONSE_DATA_KEY).set("");

            SqlTraceContext sqlCtx = SqlTraceContextHolder.init();
            ctx.channel().attr(SQL_CONTEXT_KEY).set(sqlCtx);
        }

        // 각 Chunk 처리 시 ThreadLocal 복구
        TraceContextHolder.init(traceId, spanId, properties.getTrace().getLevel(), Boolean.TRUE.equals(forceTrace));
        SqlTraceContextHolder.set(ctx.channel().attr(SQL_CONTEXT_KEY).get());

        // Request 데이터 누적 (중복 방지: 동일 msg 객체에 대해 한 번만 수행)
        int msgId = System.identityHashCode(msg);
        Integer lastInId = ctx.channel().attr(LAST_INBOUND_ID).get();
        if (lastInId == null || lastInId != msgId) {
            ctx.channel().attr(LAST_INBOUND_ID).set(msgId);
            String currentReq = ctx.channel().attr(REQUEST_DATA_KEY).get();
            ctx.channel().attr(REQUEST_DATA_KEY).set(accumulate(currentReq, safeToString(msg)));
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Response 데이터 누적 (중복 방지)
        int msgId = System.identityHashCode(msg);
        Integer lastOutId = ctx.channel().attr(LAST_OUTBOUND_ID).get();
        if (lastOutId == null || lastOutId != msgId) {
            ctx.channel().attr(LAST_OUTBOUND_ID).set(msgId);
            String data = safeToString(msg);
            String currentRes = ctx.channel().attr(RESPONSE_DATA_KEY).get();
            String accumulatedRes = accumulate(currentRes, data);
            ctx.channel().attr(RESPONSE_DATA_KEY).set(accumulatedRes);
        }

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

        try {
            super.write(ctx, msg, promise);
        } catch (Exception ex) {
            // super.write()가 동기적으로 던지면 promise listener가 발화하지 않아 컨텍스트가 누수됨
            logNetty(ctx, ex);
            clearContext(ctx);
            throw ex;
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            logNetty(ctx, null);
            clearContext(ctx);
        } finally {
            super.channelInactive(ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            Exception e = cause instanceof Exception ex ? ex : new RuntimeException(cause);
            ctx.channel().attr(ERROR_CONTEXT_KEY).set(e);
            logNetty(ctx, e);
            clearContext(ctx);
        } finally {
            super.exceptionCaught(ctx, cause);
        }
    }

    private void logNetty(ChannelHandlerContext ctx, Exception ex) {
        String traceId = ctx.channel().attr(TRACE_ID_KEY).get();
        if (traceId == null) return;

        // 전달받은 ex가 없으면 ERROR_CONTEXT에서 확인
        if (ex == null) {
            ex = ctx.channel().attr(ERROR_CONTEXT_KEY).get();
        }

        // 만약 정상 종료 시도인데 이미 에러가 예약되어 있다면, 여기서 로깅하지 않고 exceptionCaught가 처리하도록 함
        if (ex == null && ctx.channel().attr(ERROR_CONTEXT_KEY).get() != null) {
            return;
        }

        // 로깅 확정 시 traceId 소모
        ctx.channel().attr(TRACE_ID_KEY).set(null);

        String spanId = ctx.channel().attr(SPAN_ID_KEY).get();

        Long startNano = ctx.channel().attr(START_NANO_KEY).get();
        double elapsedMs = (startNano != null) ? (System.nanoTime() - startNano) / 1_000_000.0 : 0;

        String clientIp = ctx.channel().attr(REAL_IP_KEY).get();
        if (clientIp == null) {
            clientIp = ctx.channel().remoteAddress().toString();
        }

        String reqData = ctx.channel().attr(REQUEST_DATA_KEY).get();
        String resData = ctx.channel().attr(RESPONSE_DATA_KEY).get();
        SqlTraceContext sqlCtx = ctx.channel().attr(SQL_CONTEXT_KEY).get();
        Boolean forceTrace = ctx.channel().attr(FORCE_TRACE_KEY).get();

        if (sqlCtx != null) {
            SqlTraceContextHolder.set(sqlCtx);
        }

        // logApi 수행 전에 ThreadLocal 설정 (SQL 로깅 시 level 확인 및 traceId/spanId 사용을 위함)
        TraceContextHolder.init(traceId, spanId, properties.getTrace().getLevel(), Boolean.TRUE.equals(forceTrace));

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

        logProcessor.process(builder.build());
    }

    private void clearContext(ChannelHandlerContext ctx) {
        ctx.channel().attr(START_NANO_KEY).set(null);
        ctx.channel().attr(TRACE_ID_KEY).set(null);
        ctx.channel().attr(SPAN_ID_KEY).set(null);
        ctx.channel().attr(REQUEST_DATA_KEY).set(null);
        ctx.channel().attr(RESPONSE_DATA_KEY).set(null);
        ctx.channel().attr(SQL_CONTEXT_KEY).set(null);
        ctx.channel().attr(ERROR_CONTEXT_KEY).set(null);
        ctx.channel().attr(FORCE_TRACE_KEY).set(null);
        ctx.channel().attr(LAST_INBOUND_ID).set(null);
        ctx.channel().attr(LAST_OUTBOUND_ID).set(null);

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
