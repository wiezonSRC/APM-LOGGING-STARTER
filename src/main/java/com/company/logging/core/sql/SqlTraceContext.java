package com.company.logging.core.sql;

import com.company.logging.core.context.LogSqlContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 하나의 요청 내에서 실행된 SQL 추적 정보들을 모아두는 컨텍스트입니다.
 * 실행된 SQL 목록과 총 소요 시간을 관리합니다.
 */
public class SqlTraceContext {

    private final List<LogSqlContext> traces = new ArrayList<>();
    private long totalElapsed = 0;

    /**
     * 실행된 SQL 정보를 추가합니다.
     * @param sqlId 매퍼 ID
     * @param sql 실행된 SQL
     * @param sqlParam SQL 파라미터
     * @param elapsed 소요 시간(ms)
     */
    public void add(String sqlId, String sql, String sqlParam, long elapsed){
        traces.add(new LogSqlContext.Builder()
                .sqlId(sqlId)
                .sql(sql)
                .sqlParam(sqlParam)
                .elapsed(elapsed)
                .build());
        totalElapsed += elapsed;
    }

    public List<LogSqlContext> getTraces(){
        return traces;
    }

    public long getTotalElapsed(){
        return totalElapsed;
    }

    public int count(){
        return traces.size();
    }
}