package com.company.logging.core.process;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.context.LogContext;
import com.company.logging.core.context.LogSqlContext;
import com.company.logging.core.context.TraceContextHolder;
import com.company.logging.core.enums.LogMarker;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.error.BreadcrumbEvent;
import com.company.logging.core.error.ErrorClassifier;
import com.company.logging.core.error.ErrorFingerprinter;
import com.company.logging.core.sql.SqlTraceContextHolder;
import com.company.logging.core.support.util.LogMessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractLogProcessor<T extends LogContext> {
    protected final LoggingProperties properties;
    protected final Logger logger = LoggerFactory.getLogger("Log");


    protected AbstractLogProcessor(LoggingProperties properties){
        this.properties = properties;
    }

    protected abstract void logApi(T ctx);

    /**
     * 로깅 실패가 비즈니스 요청으로 전파되지 않도록 감싸는 Fail-safe 진입점입니다.
     * Filter·Listener·Handler에서는 logApi() 대신 이 메서드를 호출해야 합니다.
     */
    public final void process(T ctx) {
        try {
            logApi(ctx);
        } catch (Exception ex) {
            logger.error("[LOGGING_INTERNAL_ERROR] traceId={} cause={}", ctx.getTraceId(), ex.getMessage());
        }
    }

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
                    LogMarker.SQL.marker(),
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
                        marker.marker(),
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
            logger.info(LogMarker.SQL.marker(), LogMessageBuilder.buildSqlOmitted(traceId, spanId, omittedCount));
        }
    }


    /**
     * 공통 예외 로그. ErrorFingerprinter로 버그 지문을 생성하고 Breadcrumb를 함께 출력합니다.
     * 동일한 errorFingerprint 값 = 동일 버그 → Grafana에서 집계·알림 설정 가능합니다.
     */
    protected void logException(LogContext ctx, String traceId, String spanId) {
        if (!logger.isInfoEnabled()) {
            return;
        }

        if (ctx.getEx() == null) {
            return;
        }

        Throwable ex = ctx.getEx();
        String fingerprint = ErrorFingerprinter.fingerprint(ex);
        ErrorClassifier.ErrorType errorType = ErrorClassifier.classify(ex);
        List<BreadcrumbEvent> breadcrumbs = TraceContextHolder.getBreadcrumbs();
        LogMarker marker = resolveErrorMarker(errorType);

        logger.info(
                marker.marker(),
                LogMessageBuilder.buildError(
                        traceId,
                        spanId,
                        fingerprint,
                        errorType.getLabel(),
                        breadcrumbs,
                        ex,
                        properties.getLimit().getMaxStackDepth(),
                        properties.getLimit().getMaxStackLines()
                )
        );
    }

    private LogMarker resolveErrorMarker(ErrorClassifier.ErrorType errorType) {
        return switch (errorType) {
            case BIZ -> LogMarker.ERROR_BIZ;
            case EXTERNAL -> LogMarker.ERROR_EXTERNAL;
            default -> LogMarker.ERROR_SYSTEM;
        };
    }

}
