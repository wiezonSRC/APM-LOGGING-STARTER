package com.company.logging.support.sql.util;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis의 MappedStatement와 파라미터를 기반으로 완성된 SQL을 생성하는 유틸리티 클래스입니다.
 * '?' 플레이스홀더를 실제 값으로 치환하여 실행 가능한 SQL 형태를 만듭니다.
 */
public class SQLUtil {

    /**
     * 바인딩된 SQL과 파라미터를 사용하여 완성된 SQL 문자열을 생성합니다.
     *
     * @param ms MappedStatement
     * @param param 파라미터 객체
     * @return 파라미터가 치환된 SQL 문자열
     */
    public static String buildSql(MappedStatement ms, Object param){
        BoundSql boundSql = ms.getBoundSql(param);
        String sql = boundSql.getSql();
        List<ParameterMapping> mappings = boundSql.getParameterMappings();

        if(mappings == null || mappings.isEmpty()){
            return sql;
        }

        return replacePlaceholders(sql, mappings, boundSql, ms.getConfiguration(), param);
    }

    /**
     * SQL 문자열 내의 '?'를 순차적으로 파라미터 값으로 치환합니다.
     * 문자열 리터럴이나 주석 내의 '?'는 무시합니다.
     */
    private static String replacePlaceholders(String sql, List<ParameterMapping> mappings, BoundSql boundSql, Configuration configuration, Object param) {
        StringBuilder sb = new StringBuilder();
        SqlContext ctx = SqlContext.NORMAL;

        int paramIdx = 0;
        MetaObject metaObject = param == null ? null : configuration.newMetaObject(param);
        for(int i = 0; i < sql.length(); i++){
            char c = sql.charAt(i);

            // SQL 파싱 컨텍스트 상태 관리 (따옴표, 주석 등)
            if(ctx==SqlContext.NORMAL){
                if(c == '\'') ctx = SqlContext.SINGLE_QUOTE;
                else if(c == '-' && i+1 <sql.length() && sql.charAt(i+1) == '-') ctx = SqlContext.LINE_COMMENT;
                else if(c == '/' && i+1 < sql.length() && sql.charAt(i+1) == '*') ctx = SqlContext.BLOCK_COMMENT;
            }else if (ctx == SqlContext.SINGLE_QUOTE && c == '\'') {
                ctx = SqlContext.NORMAL;
            } else if (ctx == SqlContext.LINE_COMMENT && c == '\n') {
                ctx = SqlContext.NORMAL;
            } else if (ctx == SqlContext.BLOCK_COMMENT && c == '*' && i + 1 < sql.length() && sql.charAt(i + 1) == '/') {
                ctx = SqlContext.NORMAL;
            }


            // '?' 치환 로직
            if(c == '?' && ctx == SqlContext.NORMAL && paramIdx < mappings.size()){
                ParameterMapping pm = mappings.get(paramIdx++);
                Object value = resolveValue(pm, boundSql, metaObject);
                sb.append(formatValue(value));
                continue;
            }

            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 파라미터 매핑 정보를 통해 실제 값을 추출합니다.
     */
    private static Object resolveValue(ParameterMapping pm, BoundSql boundSql, MetaObject metaObject) {
        String prop = pm.getProperty();

        if(boundSql.hasAdditionalParameter(prop)){
            return boundSql.getAdditionalParameter(prop);
        }

        if(metaObject != null && metaObject.hasGetter(prop)){
            return metaObject.getValue(prop);
        }

        return null;
    }

    /**
     * 값을 SQL 리터럴 형식으로 변환합니다. (예: 문자열은 따옴표로 감쌈)
     */
    private static String formatValue(Object value) {
        if(value == null) return "NULL";

        if(value instanceof String){
            return "'" + ((String) value).replace("'", "''") + "'";
        }

        if(value instanceof LocalDateTime){
            return "'" + value + "'";
        }

        if(value instanceof Number || value instanceof Boolean ){
            return value.toString();
        }

        return "'" + value + "'";
    }


}