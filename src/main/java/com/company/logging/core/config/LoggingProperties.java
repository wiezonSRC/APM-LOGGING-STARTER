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
    private final Capture capture = new Capture();
    private final Async async = new Async();

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
     * 캡처 모드를 정의하는 열거형입니다.
     */
    public enum CaptureMode {
        ALWAYS, ERROR, SLOW, SAMPLE, OFF
    }

    /**
     * 무엇을 캡처할지 결정하는 설정을 담는 내부 클래스입니다.
     */
    public static class Capture {
        private CaptureMode body = CaptureMode.ERROR;
        private CaptureMode sql = CaptureMode.SLOW;
        private double sampleRate = 0.01; // 1%

        public CaptureMode getBody() {
            return body;
        }

        public void setBody(CaptureMode body) {
            this.body = body;
        }

        public CaptureMode getSql() {
            return sql;
        }

        public void setSql(CaptureMode sql) {
            this.sql = sql;
        }

        public double getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
        }
    }

    /**
     * 로깅 제한(OOM 방지) 설정을 담는 내부 클래스입니다.
     */
    public static class Limit {
        private int maxSqlCount = 100;
        private int maxSqlDetailCount = 10;     // 상세 정보를 남길 최대 SQL 개수
        private int maxSqlLength = 2000;
        private int maxSqlParamLength = 1000;
        private int maxBodyLength = 2000;
        private int maxStackDepth = 5;          // Caused by 최대 깊이
        private int maxStackLines = 3;          // 각 Cause당 라인 수
        private int n1DetectionThreshold = 3;   // 동일 Mapper가 이 횟수 이상 호출되면 N+1 경고

        public int getMaxSqlCount() {
            return maxSqlCount;
        }

        public void setMaxSqlCount(int maxSqlCount) {
            this.maxSqlCount = maxSqlCount;
        }

        public int getMaxSqlDetailCount() {
            return maxSqlDetailCount;
        }

        public void setMaxSqlDetailCount(int maxSqlDetailCount) {
            this.maxSqlDetailCount = maxSqlDetailCount;
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

        public int getMaxStackDepth() {
            return maxStackDepth;
        }

        public void setMaxStackDepth(int maxStackDepth) {
            this.maxStackDepth = maxStackDepth;
        }

        public int getMaxStackLines() {
            return maxStackLines;
        }

        public void setMaxStackLines(int maxStackLines) {
            this.maxStackLines = maxStackLines;
        }

        public int getN1DetectionThreshold() {
            return n1DetectionThreshold;
        }

        public void setN1DetectionThreshold(int n1DetectionThreshold) {
            this.n1DetectionThreshold = n1DetectionThreshold;
        }
    }

    /**
     * 슬로우 쿼리(Slow Query) 관련 설정을 담는 내부 클래스입니다.
     */
    public static class Slow{

        // log.slow.query 설정을 매핑
        private Query query = new Query();
        private int apiMs = 1000; // API 전체 임계값

        public int getApiMs() {
            return apiMs;
        }

        public void setApiMs(int apiMs) {
            this.apiMs = apiMs;
        }

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
    public Capture getCapture() {
        return capture;
    }

    public Async getAsync() {
        return async;
    }

    public enum OverflowStrategy {
        DROP, SYNC
    }

    /**
     * 비동기 로그 처리 설정입니다.
     * log.async.enabled=true 시 로깅 작업을 데몬 워커 스레드로 위임합니다.
     */
    public static class Async {
        private boolean enabled = false;
        private int queueSize = 8192;
        private int threadCount = 1;
        private OverflowStrategy overflowStrategy = OverflowStrategy.DROP;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public void setQueueSize(int queueSize) {
            this.queueSize = queueSize;
        }

        public int getThreadCount() {
            return threadCount;
        }

        public void setThreadCount(int threadCount) {
            this.threadCount = threadCount;
        }

        public OverflowStrategy getOverflowStrategy() {
            return overflowStrategy;
        }

        public void setOverflowStrategy(OverflowStrategy overflowStrategy) {
            this.overflowStrategy = overflowStrategy;
        }
    }
}
