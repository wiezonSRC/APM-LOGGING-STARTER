package com.company.logging.sql;

import java.util.List;

/**
 * 스레드 로컬(ThreadLocal)을 사용하여 현재 스레드의 SqlTraceContext를 관리하는 홀더 클래스입니다.
 */
public class SqlTraceContextHolder {

    private static ThreadLocal<SqlTraceContext> CTX = new ThreadLocal<>();

    /**
     * 컨텍스트를 초기화합니다. 요청 시작 시 호출되어야 합니다.
     */
    public static void init(){
        CTX.set(new SqlTraceContext());
    }

    /**
     * 현재 스레드에 저장된 모든 SQL 추적 목록을 반환합니다.
     */
    public static List<SqlTrace> getAll(){
        SqlTraceContext ctx = CTX.get();
        return ctx != null ? ctx.getTraces() : List.of();
    }

    /**
     * 현재 스레드의 SqlTraceContext를 반환합니다.
     */
    public static SqlTraceContext get(){
        return CTX.get();
    }

    /**
     * 현재까지 누적된 총 SQL 실행 시간을 반환합니다.
     */
    public static long totalElapsed(){
        long total = 0;
        
        if(CTX.get() != null){
            total = CTX.get().getTotalElapsed();
        }

        return total;
    }

    /**
     * 컨텍스트를 제거합니다. 요청 종료 시 호출되어야 합니다.
     */
    public static void clear(){
        CTX.remove();
    }

}