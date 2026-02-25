package com.company.logging.core.process;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.context.LogContext;
import com.company.logging.core.context.LogSqlContext;
import com.company.logging.core.context.TraceContextHolder;
import com.company.logging.core.enums.LogMarker;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.support.util.LogMessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLogProcessor<T extends LogContext> {
    protected final LoggingProperties properties;
    protected final Logger logger = LoggerFactory.getLogger("Log");


    protected AbstractLogProcessor(LoggingProperties properties){
        this.properties = properties;
    }

    public abstract void logApi(T ctx);

    /**
     * TraceLevel 계산 공통 로직
     */
    protected TraceLevel resolveLevel(){
        if(TraceContextHolder.isTrace()) return TraceLevel.TRACE;
        if(TraceContextHolder.isDebug()) return TraceLevel.DEBUG;
        return TraceLevel.PROD;
    }

    /**
     * 공통 SQL 로깅
     */
    protected void logSqlDetails(String traceId,
                                 String spanId,
                                 TraceLevel level,
                                 boolean isError) {

        long totalSqlElapsed = SqlTraceContextHolder.totalElapsed();
        int totalSlowLimit = properties.getSlow().getQuery().getTotalMs();
        int slowQueryLimit = properties.getSlow().getQuery().getMs();

        if (totalSqlElapsed >= totalSlowLimit) {
            logger.info(
                    LogMessageBuilder.buildTotalSlow(
                            traceId,
                            spanId,
                            totalSqlElapsed,
                            totalSlowLimit
                    )
            );
        }

        for (LogSqlContext sql : SqlTraceContextHolder.getAll()) {

            boolean isSlow = sql.getElapsed() >= slowQueryLimit;

            if (isSlow) {

                logger.info(
                        LogMessageBuilder.buildSql(
                                LogMarker.SLOW_SQL,
                                traceId,
                                spanId,
                                sql.getSqlId(),
                                sql.getElapsed(),
                                sql.getSql()
                        )
                );

            } else if (level == TraceLevel.TRACE || isError) {

                logger.info(
                        LogMessageBuilder.buildSql(
                                LogMarker.SQL,
                                traceId,
                                spanId,
                                sql.getSqlId(),
                                sql.getElapsed(),
                                sql.getSql()
                        )
                );

            } else if (level == TraceLevel.DEBUG) {

                logger.info(
                        LogMessageBuilder.buildSqlParam(
                                LogMarker.SQL,
                                traceId,
                                spanId,
                                sql.getSqlId(),
                                sql.getElapsed(),
                                sql.getSqlParam()
                        )
                );
            }
        }
    }


    /**
     * 공통 예외 로그
     */
    protected void logException(LogContext ctx, String traceId, String spanId) {

        if (ctx.getEx() == null) return;

        logger.error(
                LogMessageBuilder.buildException(
                        LogMarker.EXCEPTION,
                        traceId,
                        spanId,
                        ctx.getEx()
                ),
                ctx.getEx()
        );
    }

}
