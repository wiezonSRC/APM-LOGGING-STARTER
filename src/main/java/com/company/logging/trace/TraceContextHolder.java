package com.company.logging.trace;

/**
 * 현재 스레드의 추적 레벨(Trace Level)과 강제 추적 여부를 저장하는 컨텍스트 홀더입니다.
 * <p>
 * ThreadLocal을 사용하여 요청 단위로 컨텍스트를 관리합니다.
 */
public class TraceContextHolder {

    private static final ThreadLocal<TraceLevel> LEVEL = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORCE_TRACE = new ThreadLocal<>();

    private TraceContextHolder() {}
    /**
     * 컨텍스트를 초기화합니다.
     * @param level 추적 레벨
     * @param forceTrace 강제 추적 여부
     */
    public static void init(TraceLevel level, boolean forceTrace){
        LEVEL.set(level);
        FORCE_TRACE.set(forceTrace);
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
     * 현재 상태가 DEBUG 레벨인지 확인합니다.
     */
    public static boolean isDebug(){
        return level() == TraceLevel.DEBUG;
    }

    /**
     * ThreadLocal에 저장된 컨텍스트 정보를 제거합니다.
     */
    public static void clear(){
        LEVEL.remove();
        FORCE_TRACE.remove();
    }


}