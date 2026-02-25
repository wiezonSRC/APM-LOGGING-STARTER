package com.company.logging.netty.process;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.enums.LogMarker;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.process.AbstractLogProcessor;
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
        boolean isError = ctx.getEx() != null;

        logger.info(
                "[{}] trace_id={} span_id={} client_ip={} status={} elapsed={}ms",
                LogMarker.NETTY_PROD,
                traceId,
                spanId,
                ctx.getClientIp(),
                ctx.getStatus(),
                String.format("%.3f", ctx.getElapsedMs())
        );

        logSqlDetails(traceId, spanId, level, isError);
        logException(ctx, traceId, spanId);
    }
}
