package com.company.logging.core.context;

import com.company.logging.core.enums.TraceLevel;

/**
 * 현재 스레드의 추적 레벨(Trace Level)과 강제 추적 여부를 저장하는 컨텍스트 홀더입니다.
 * <p>
 * ThreadLocal을 사용하여 요청 단위로 컨텍스트를 관리합니다.
 */
public class TraceContextHolder {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAN_ID = new ThreadLocal<>();
    private static final ThreadLocal<TraceLevel> LEVEL = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORCE_TRACE = new ThreadLocal<>();

    private TraceContextHolder() {}
    /**
     * 컨텍스트를 초기화합니다.
     * @param traceId 추적 ID
     * @param spanId 스팬 ID
     * @param level 추적 레벨
     * @param forceTrace 강제 추적 여부
     */
    public static void init(String traceId, String spanId, TraceLevel level, boolean forceTrace){
        TRACE_ID.set(traceId);
        SPAN_ID.set(spanId);
        LEVEL.set(level);
        FORCE_TRACE.set(forceTrace);
    }

    /**
     * 현재 설정된 Trace ID를 반환합니다.
     */
    public static String traceId() {
        return TRACE_ID.get();
    }

    /**
     * 현재 설정된 Span ID를 반환합니다.
     */
    public static String spanId() {
        return SPAN_ID.get();
    }

    /**
     * 현재 설정된 추적 레벨을 반환합니다.
     */
    public static TraceLevel level(){
        return LEVEL.get();
    }

    /**
     * 강제 추적 모드인지 확인합니다.
     */
    public static boolean isForceTrace(){
        return Boolean.TRUE.equals(FORCE_TRACE.get());
    }

    /**
     * 현재 상태가 TRACE 레벨이거나 강제 추적 모드인지 확인합니다.
     */
    public static boolean isTrace(){
        return level() == TraceLevel.TRACE || isForceTrace();
    }

    /**
     * ThreadLocal에 저장된 컨텍스트 정보를 제거합니다.
     */
    public static void clear(){
        TRACE_ID.remove();
        SPAN_ID.remove();
        LEVEL.remove();
        FORCE_TRACE.remove();
    }


}