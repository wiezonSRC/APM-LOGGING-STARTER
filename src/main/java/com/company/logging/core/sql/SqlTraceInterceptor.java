package com.company.logging.core.sql;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.context.TraceContextHolder;
import com.company.logging.core.enums.LogMarker;
import com.company.logging.core.support.sql.SQLUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.List;

import com.company.logging.core.config.LoggingPropertiesHolder;

/**
 * MyBatis 실행을 가로채어 SQL 실행 정보를 수집하는 인터셉터입니다.
 * Executor의 update 및 query 메서드를 가로챕니다.
 */
@Intercepts({
        @Signature(
                type = Executor.class,
                method = "update",
                args = {MappedStatement.class, Object.class}
        ),
        @Signature(
                type = Executor.class,
                method = "query",
                args = {
                        MappedStatement.class,
                        Object.class,
                        RowBounds.class,
                        ResultHandler.class
                }
        ),
        @Signature(
                type = Executor.class,
                method = "query",
                args = {
                        MappedStatement.class,
                        Object.class,
                        RowBounds.class,
                        ResultHandler.class,
                        CacheKey.class,
                        BoundSql.class
                }
        )
})
public class SqlTraceInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger("Log");

    private final LoggingProperties properties;

    // CachingExecutor → SimpleExecutor 재진입 방지용 플래그
    private static final ThreadLocal<Boolean> IS_LOGGING = ThreadLocal.withInitial(() -> false);

    public SqlTraceInterceptor() {
        this(null);
    }

    public SqlTraceInterceptor(LoggingProperties properties) {
        this.properties = properties;
    }

    private LoggingProperties getProperties() {
        if (this.properties != null) {
            return this.properties;
        }

        return LoggingPropertiesHolder.getProperties();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        long start = System.currentTimeMillis();
        boolean isError = false;

        LoggingProperties props = getProperties();

        if (props == null) {
            props = new LoggingProperties();
        }

        if (Boolean.TRUE.equals(IS_LOGGING.get())) {
            return invocation.proceed();
        }

        IS_LOGGING.set(true);

        try {
            return invocation.proceed();
        } catch (Throwable t) {
            isError = true;
            throw t;
        } finally {
            long elapsed = System.currentTimeMillis() - start;

            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object param = args.length > 1 ? args[1] : null;
            String sqlId = ms.getId();

            SqlTraceContext ctx = SqlTraceContextHolder.get();

            if (ctx != null) {
                int maxCount = props.getLimit().getMaxSqlCount();
                int maxDetailCount = props.getLimit().getMaxSqlDetailCount();

                boolean isFull = ctx.isFull(maxCount);

                // 에러 발생 시에는 꽉 찼더라도 공간을 만들어서 저장함
                if (isFull && isError) {
                    ctx.removeOldestNormal();
                    isFull = false;
                }

                if (isFull) {
                    ctx.addOmitted();
                } else {
                    // 상세 정보를 남길 것인지 결정 (에러이거나, 상세 개수 제한 내인 경우)
                    boolean includeDetail = isError || !ctx.isDetailFull(maxDetailCount);

                    String sql = null;
                    String sqlParam = null;

                    if (includeDetail) {
                        int maxSqlLen = props.getLimit().getMaxSqlLength();
                        int maxParamLen = props.getLimit().getMaxSqlParamLength();

                        // getBoundSql()을 한 번만 호출하여 sql·param 추출에 재사용
                        BoundSql boundSql = ms.getBoundSql(param);

                        sql = SQLUtil.buildSql(boundSql, param, ms.getConfiguration(), maxSqlLen);
                        sqlParam = extractSqlParam(boundSql, ms.getConfiguration(), param, maxParamLen);
                    }

                    ctx.add(sqlId, sql, sqlParam, elapsed, isError, includeDetail);
                }
            }

            // SQL 실행 이력을 Breadcrumb에 기록 — 에러 발생 시 원인 추적 경로 제공
            TraceContextHolder.addBreadcrumb(
                isError ? "SQL_ERROR" : "SQL",
                sqlId + " " + elapsed + "ms"
            );

            // N+1 감지: 동일 Mapper가 임계값에 딱 도달하는 시점에 한 번만 경고
            if (ctx != null) {
                int callCount = ctx.incrementCallCount(sqlId);
                int threshold = props.getLimit().getN1DetectionThreshold();

                if (callCount == threshold) {
                    logger.warn(
                        LogMarker.N1_QUERY.marker(),
                        "trace_id={} sql_id={} call_count={} possible N+1 detected — consider batch fetch or IN clause",
                        TraceContextHolder.traceId(), sqlId, callCount
                    );
                }
            }

            // 재진입 보호 플래그는 finally 블록 내 모든 처리가 끝난 뒤 해제
            IS_LOGGING.remove();
        }
    }

    /**
     * SQL 파라미터를 문자열로 추출합니다.
     * 이미 생성된 BoundSql을 받아 getBoundSql() 이중 호출을 방지합니다.
     */
    private String extractSqlParam(BoundSql boundSql, Configuration configuration, Object param, int maxLength) {
        if (param == null) {
            return null;
        }

        List<ParameterMapping> mappings = boundSql.getParameterMappings();

        if (mappings == null || mappings.isEmpty()) {
            return null;
        }

        MetaObject metaObject = configuration.newMetaObject(param);
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (ParameterMapping pm : mappings) {
            if (sb.length() >= maxLength) {
                sb.append("...(TRUNCATED)");
                break;
            }

            String prop = pm.getProperty();
            Object value;

            if (boundSql.hasAdditionalParameter(prop)) {
                value = boundSql.getAdditionalParameter(prop);
            } else if (metaObject.hasGetter(prop)) {
                value = metaObject.getValue(prop);
            } else {
                value = null;
            }

            if (!first) {
                sb.append(", ");
            }

            sb.append(prop).append("=").append(formatValue(value));
            first = false;
        }

        sb.append("}");

        return sb.toString();
    }

    /**
     * 값을 로그에 적합한 문자열 형식으로 변환합니다.
     * 마스킹은 여기서 수행하지 않고, 렌더 시점(AbstractLogProcessor.logSqlDetails)에서
     * log.security.masking-enabled 플래그에 따라 일괄 적용합니다(이중 마스킹 방지).
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof String s) {
            return "'" + s + "'";
        }

        if (value instanceof java.util.Date d) {
            return "'" + d + "'";
        }

        return String.valueOf(value);
    }
}