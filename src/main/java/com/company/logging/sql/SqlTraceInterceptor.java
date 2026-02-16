package com.company.logging.sql;

import com.company.logging.support.sql.SQLUtil;
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
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();

        try{
            return invocation.proceed();
        }finally{

            long elapsed = System.currentTimeMillis() - start;

            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object param = args.length > 1 ? args[1] : null;
            String sqlId = ms.getId();


            String sql = null;
            SqlTraceContext ctx = SqlTraceContextHolder.get();

            if(ctx != null){
                sql = SQLUtil.buildSql(ms, param);
            }

            if(ctx != null){
                ctx.add(sqlId, sql, extractSqlParam(ms, param),elapsed);
            }
        }

    }

    /**
     * SQL 파라미터를 문자열로 추출합니다.
     */
    private String extractSqlParam(MappedStatement ms, Object param) {
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