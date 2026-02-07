package com.company.logging.config;

import com.company.logging.trace.TraceLevel;
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
}
