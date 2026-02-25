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
    private int omittedCount = 0;

    /**
     * 실행된 SQL 정보를 추가합니다.
     * @param sqlId 매퍼 ID
     * @param sql 실행된 SQL
     * @param sqlParam SQL 파라미터
     * @param elapsed 소요 시간(ms)
     * @param isError 에러 발생 여부
     */
    public void add(String sqlId, String sql, String sqlParam, long elapsed, boolean isError){
        traces.add(new LogSqlContext.Builder()
                .sqlId(sqlId)
                .sql(sql)
                .sqlParam(sqlParam)
                .elapsed(elapsed)
                .isError(isError)
                .build());
        totalElapsed += elapsed;
    }

    /**
     * 에러 쿼리를 위해 공간을 확보하기 위해 가장 오래된 정상 쿼리를 제거합니다.
     */
    public void removeOldestNormal() {
        for (int i = 0; i < traces.size(); i++) {
            if (!traces.get(i).isError()) {
                traces.remove(i);
                return;
            }
        }
        // 모든 쿼리가 에러 쿼리라면 가장 오래된 것 제거
        if (!traces.isEmpty()) {
            traces.remove(0);
        }
    }

    /**
     * SQL 로깅이 생략되었음을 기록합니다.
     */
    public void addOmitted() {
        this.omittedCount++;
    }

    public int getOmittedCount() {
        return omittedCount;
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

    public boolean isFull(int maxCount) {
        return traces.size() >= maxCount;
    }
}