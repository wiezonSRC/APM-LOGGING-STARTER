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
        LoggingProperties.CaptureMode sqlMode = properties.getCapture().getSql();

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
            
            // 정책에 따른 로깅 여부 결정
            boolean shouldLog = false;
            
            if (sqlMode == LoggingProperties.CaptureMode.ALWAYS) {
                shouldLog = true;
            } else if (sqlMode == LoggingProperties.CaptureMode.ERROR && (isError || sql.isError())) {
                shouldLog = true;
            } else if (sqlMode == LoggingProperties.CaptureMode.SLOW && (isSlow || totalSqlElapsed >= totalSlowLimit)) {
                shouldLog = true;
            } else if (sqlMode == LoggingProperties.CaptureMode.SAMPLE) {
                // 샘플링 로직은 Filter 등에서 결정된 forceTrace나 level로 판단
                shouldLog = level == TraceLevel.TRACE;
            } else if (level == TraceLevel.TRACE) {
                shouldLog = true;
            }

            if (shouldLog || isError || sql.isError() || isSlow) {

                LogMarker marker;
                if (sql.isError()) {
                    marker = LogMarker.SQL_EXCEPTION;
                } else if (isSlow) {
                    marker = LogMarker.SQL_SLOW;
                } else {
                    marker = LogMarker.SQL;
                }

                String sqlText = (sql.getSql() != null) ? sql.getSql() : "[SQL TEXT OMITTED BY POLICY]";
                // TRACE 레벨이거나 상세 정보가 있을 때만 파라미터를 로그에 포함
                String sqlParam = (level == TraceLevel.TRACE) ? sql.getSqlParam() : null;

                logger.info(
                        LogMessageBuilder.buildSql(
                                marker,
                                traceId,
                                spanId,
                                sql.getSqlId(),
                                sql.getElapsed(),
                                sqlText,
                                sqlParam
                        )
                );
            }
        }

        int omittedCount = SqlTraceContextHolder.get() != null ? SqlTraceContextHolder.get().getOmittedCount() : 0;
        if (omittedCount > 0) {
            logger.info(LogMessageBuilder.buildSqlOmitted(traceId, spanId, omittedCount));
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
                        ctx.getEx(),
                        properties.getLimit().getMaxStackDepth(),
                        properties.getLimit().getMaxStackLines()
                )
        );
    }

}
