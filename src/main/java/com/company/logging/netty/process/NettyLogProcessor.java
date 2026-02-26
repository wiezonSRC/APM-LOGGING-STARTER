package com.company.logging.netty.process;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.enums.LogMarker;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.process.AbstractLogProcessor;
import com.company.logging.core.support.util.CommonUtil;
import com.company.logging.netty.context.LogNettyContext;

public class NettyLogProcessor extends AbstractLogProcessor<LogNettyContext> {
    public NettyLogProcessor(LoggingProperties properties){
        super(properties);
    }

    @Override
    public void logApi(LogNettyContext ctx) {
        String traceId = ctx.getTraceId(); 
        String spanId = ctx.getSpanId();
        TraceLevel level = resolveLevel();
        LoggingProperties.CaptureMode bodyMode = properties.getCapture().getBody();

        boolean isError = ctx.getEx() != null;
        boolean isSlow = ctx.getElapsedMs() >= properties.getSlow().getApiMs();

        // 1. 기본 요약 로그 (항상 출력)
        logger.info(
                "[{}] trace_id={} span_id={} client_ip={} status={} elapsed={}ms",
                LogMarker.NETTY_PROD,
                traceId,
                spanId,
                ctx.getClientIp(),
                ctx.getStatus(),
                String.format("%.3f", ctx.getElapsedMs())
        );

        // 2. 바디 로깅 여부 결정
        boolean shouldLogBody = false;
        if (bodyMode == LoggingProperties.CaptureMode.ALWAYS) {
            shouldLogBody = true;
        } else if (bodyMode == LoggingProperties.CaptureMode.ERROR && isError) {
            shouldLogBody = true;
        } else if (bodyMode == LoggingProperties.CaptureMode.SLOW && isSlow) {
            shouldLogBody = true;
        } else if (bodyMode == LoggingProperties.CaptureMode.SAMPLE) {
            shouldLogBody = level == TraceLevel.TRACE;
        } else if (level == TraceLevel.TRACE) {
            shouldLogBody = true;
        }

        if (shouldLogBody || isError) {
            int maxBodyLen = properties.getLimit().getMaxBodyLength();
            String reqData = CommonUtil.truncate(ctx.getRequestData(), maxBodyLen);
            String resData = CommonUtil.truncate(ctx.getResponseData(), maxBodyLen);
            
            LogMarker marker = isError ? LogMarker.EXCEPTION : LogMarker.API_TRACE;

            logger.info(
                    "[{}] trace_id={} span_id={} request={} response={}",
                    marker,
                    traceId,
                    spanId,
                    reqData,
                    resData
            );
        }

        logSqlDetails(traceId, spanId, level, isError);
        logException(ctx, traceId, spanId);
    }
}
