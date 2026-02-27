package com.company.logging.core.sql;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.support.sql.SQLUtil;
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
        )
})
public class SqlTraceInterceptor implements Interceptor {

    private final LoggingProperties properties;

    public SqlTraceInterceptor(LoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        boolean isError = false;

        if (invocation.getTarget() instanceof CachingExecutor) {
            return invocation.proceed();
        }

        try{
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

            if(ctx != null){
                int maxCount = properties.getLimit().getMaxSqlCount();
                int maxDetailCount = properties.getLimit().getMaxSqlDetailCount();
                
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
                        int maxSqlLen = properties.getLimit().getMaxSqlLength();
                        int maxParamLen = properties.getLimit().getMaxSqlParamLength();

                        sql = SQLUtil.buildSql(ms, param, maxSqlLen);
                        sqlParam = extractSqlParam(ms, param, maxParamLen);
                    }

                    ctx.add(sqlId, sql, sqlParam, elapsed, isError, includeDetail);
                }
            }
        }

    }

    /**
     * SQL 파라미터를 문자열로 추출합니다.
     */
    private String extractSqlParam(MappedStatement ms, Object param, int maxLength) {
        if(param == null) return null;

        BoundSql boundSql = ms.getBoundSql(param);
        List<ParameterMapping> mappings = boundSql.getParameterMappings();

        if(mappings == null || mappings.isEmpty()){
            return null;
        }

        Configuration configuration = ms.getConfiguration();
        MetaObject metaObject = configuration.newMetaObject(param);

        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for(ParameterMapping pm : mappings){
            if (sb.length() >= maxLength) {
                sb.append("...(TRUNCATED)");
                break;
            }

            String prop = pm.getProperty();
            Object value;

            if(boundSql.hasAdditionalParameter(prop)){
                value = boundSql.getAdditionalParameter(prop);
            }else if(metaObject.hasGetter(prop)){
                value = metaObject.getValue(prop);
            }else{
                value = null;
            }

            if(!first) sb.append(", ");
            sb.append(prop).append("=").append(formatValue(value));
            first = false;
        }

        sb.append("}");

        return sb.toString();
    }

    /**
     * 값을 로그에 적합한 문자열 형식으로 변환합니다.
     */
    private String formatValue(Object value) {
        if(value == null) return "null";

        if(value instanceof  String s){
            return "'" + s + "'";
        }
        if(value instanceof java.util.Date d){
            return "'" + d + "'";
        }


        return String.valueOf(value);
    }
}