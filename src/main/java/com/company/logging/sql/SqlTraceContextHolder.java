package com.company.logging.sql;

import com.company.logging.context.LogSqlContext;
import java.util.List;

/**
 * 스레드 로컬(ThreadLocal)을 사용하여 현재 스레드의 SqlTraceContext를 관리하는 홀더 클래스입니다.
 */
public class SqlTraceContextHolder {

    private static final ThreadLocal<SqlTraceContext> contextThreadLocal = new ThreadLocal<>();
    private SqlTraceContextHolder() {}
    /**
     * 컨텍스트를 초기화합니다. 요청 시작 시 호출되어야 합니다.
     */
    public static void init(){
        contextThreadLocal.set(new SqlTraceContext());
    }

    /**
     * 현재 스레드에 저장된 모든 SQL 추적 목록을 반환합니다.
     */
    public static List<LogSqlContext> getAll(){
        SqlTraceContext ctx = contextThreadLocal.get();
        return ctx != null ? ctx.getTraces() : List.of();
    }

    /**
     * 현재 스레드의 SqlTraceContext를 반환합니다.
     */
    public static SqlTraceContext get(){
        return contextThreadLocal.get();
    }

    /**
     * 현재까지 누적된 총 SQL 실행 시간을 반환합니다.
     */
    public static long totalElapsed(){
        long total = 0;
        
        if(contextThreadLocal.get() != null){
            total = contextThreadLocal.get().getTotalElapsed();
        }

        return total;
    }

    /**
     * 컨텍스트를 제거합니다. 요청 종료 시 호출되어야 합니다.
     */
    public static void clear(){
        contextThreadLocal.remove();
    }

}