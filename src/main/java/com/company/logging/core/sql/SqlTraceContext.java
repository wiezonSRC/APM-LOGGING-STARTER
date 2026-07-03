package com.company.logging.core.sql;

import com.company.logging.core.context.LogSqlContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 하나의 요청 내에서 실행된 SQL 추적 정보들을 모아두는 컨텍스트입니다.
 * 실행된 SQL 목록과 총 소요 시간을 관리합니다.
 */
public class SqlTraceContext {

    private final List<LogSqlContext> traces = new ArrayList<>();
    private final Map<String, Integer> sqlIdCallCount = new HashMap<>();
    private long totalElapsed = 0;
    private int omittedCount = 0;

    /**
     * 실행된 SQL 정보를 추가합니다.
     * @param sqlId 매퍼 ID
     * @param sql 실행된 SQL
     * @param sqlParam SQL 파라미터
     * @param elapsed 소요 시간(ms)
     * @param isError 에러 발생 여부
     * @param includeDetail 상세 정보(SQL, Param) 포함 여부
     */
    public void add(String sqlId, String sql, String sqlParam, long elapsed, boolean isError, boolean includeDetail){
        LogSqlContext.Builder builder = new LogSqlContext.Builder()
                .sqlId(sqlId)
                .elapsed(elapsed)
                .isError(isError);

        // 상세 정보 포함 조건: includeDetail가 true이거나 에러 발생 시
        if (includeDetail || isError) {
            builder.sql(sql).sqlParam(sqlParam);
        }

        traces.add(builder.build());
        totalElapsed += elapsed;
    }

    /**
     * 상세 정보를 남길 수 있는 최대 개수에 도달했는지 확인합니다.
     * (에러 쿼리는 이 제한과 무관하게 상세를 남길 수 있도록 처리 예정)
     */
    public boolean isDetailFull(int maxDetailCount) {
        return traces.size() >= maxDetailCount;
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

    /**
     * 특정 Mapper ID의 호출 횟수를 1 증가시키고 증가된 값을 반환합니다.
     * N+1 감지를 위해 SqlTraceInterceptor에서 사용합니다.
     */
    public int incrementCallCount(String sqlId) {
        return sqlIdCallCount.merge(sqlId, 1, Integer::sum);
    }
}