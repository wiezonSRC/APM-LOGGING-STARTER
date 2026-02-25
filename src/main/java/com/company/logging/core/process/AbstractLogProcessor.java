package com.company.logging.core.process;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.context.LogContext;
import com.company.logging.core.context.LogSqlContext;
import com.company.logging.core.context.TraceContextHolder;
import com.company.logging.core.enums.LogMarker;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.support.sql.SQLUtil;
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

        if (!logger.isInfoEnabled()) {
            return;
        }

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
            // 해당 SQL 자체가 에러를 유발했거나 전체 API가 에러인 경우 상세 로깅
            boolean shouldLogFull = sql.isError() || level == TraceLevel.TRACE || isError;

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

            } else if (shouldLogFull) {

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
            } else if(level == TraceLevel.PROD){

                logger.info(
                        LogMessageBuilder.buildSql(
                                LogMarker.SQL,
                                traceId,
                                spanId,
                                sql.getSqlId(),
                                sql.getElapsed(),
                                SQLUtil.truncate(sql.getSql(), 200)
                        )
                );
            }
        }

        int omittedCount = SqlTraceContextHolder.get() != null ? SqlTraceContextHolder.get().getOmittedCount() : 0;
        if (omittedCount > 0) {
            logger.warn(LogMessageBuilder.buildSqlOmitted(traceId, spanId, omittedCount));
        }
    }


    /**
     * 공통 예외 로그
     */
    protected void logException(LogContext ctx, String traceId, String spanId) {

        if (!logger.isInfoEnabled()) return;
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
