package com.company.logging.core.config;

import com.company.logging.core.enums.TraceLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로깅 관련 설정 속성 클래스입니다.
 * <p>
 * "log" 접두어로 시작하는 설정값들을 매핑합니다. (예: log.trace.level, log.slow.query.ms 등)
 */
@ConfigurationProperties(prefix ="log")
public class LoggingProperties {
    private final Trace trace = new Trace();
    private final Slow slow = new Slow();
    private final Limit limit = new Limit();

    /**
     * 추적(Trace) 관련 설정을 담는 내부 클래스입니다.
     */
    public static class Trace {
        // log.trace.level 설정을 매핑
        private TraceLevel level = TraceLevel.PROD;

        public TraceLevel getLevel(){
            return level;
        }

        public void setLevel(TraceLevel level){
            this.level = (level != null) ? level : TraceLevel.PROD;
        }
    }

    /**
     * SQL 로깅 제한(OOM 방지) 설정을 담는 내부 클래스입니다.
     */
    public static class Limit {
        private int maxSqlCount = 100;
        private int maxSqlLength = 2000;
        private int maxSqlParamLength = 1000;
        private int maxBodyLength = 1000;

        public int getMaxSqlCount() {
            return maxSqlCount;
        }

        public void setMaxSqlCount(int maxSqlCount) {
            this.maxSqlCount = maxSqlCount;
        }

        public int getMaxSqlLength() {
            return maxSqlLength;
        }

        public void setMaxSqlLength(int maxSqlLength) {
            this.maxSqlLength = maxSqlLength;
        }

        public int getMaxSqlParamLength() {
            return maxSqlParamLength;
        }

        public void setMaxSqlParamLength(int maxSqlParamLength) {
            this.maxSqlParamLength = maxSqlParamLength;
        }

        public int getMaxBodyLength() {
            return maxBodyLength;
        }

        public void setMaxBodyLength(int maxBodyLength) {
            this.maxBodyLength = maxBodyLength;
        }
    }

    /**
     * 슬로우 쿼리(Slow Query) 관련 설정을 담는 내부 클래스입니다.
     */
    public static class Slow{

        // log.slow.query 설정을 매핑
        private Query query = new Query();

        /**
         * 쿼리 시간 임계값을 설정하는 내부 클래스입니다.
         */
        public static class Query{
            private int ms = 300;
            private int totalMs = 1000;

            /**
             * 단일 쿼리가 슬로우 쿼리로 간주되는 시간(ms)을 반환합니다.
             * @return 임계값 (ms)
             */
            public int getMs(){
                return this.ms;
            }

            /**
             * 요청 내 전체 쿼리 수행 시간이 슬로우로 간주되는 시간(ms)을 반환합니다.
             * @return 전체 임계값 (ms)
             */
            public int getTotalMs(){
                return this.totalMs;
            }
            public void setMs(int ms){
                this.ms = ms;
            }
            public void setTotalMs(int totalMs){
                this.totalMs = totalMs;
            }

        }

        public Query getQuery(){
            return query;
        }

        public void setQuery(Query query){
            this.query = query;
        }
    }

    public Trace getTrace(){
        return trace;
    }
    public Slow getSlow(){
        return slow;
    }
    public Limit getLimit() {
        return limit;
    }
}
