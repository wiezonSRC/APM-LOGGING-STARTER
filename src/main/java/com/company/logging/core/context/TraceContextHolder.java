package com.company.logging.core.context;

import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.error.BreadcrumbEvent;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * 현재 스레드의 추적 레벨(Trace Level)과 강제 추적 여부를 저장하는 컨텍스트 홀더입니다.
 * Breadcrumb 이벤트를 시간순으로 누적하여 에러 발생 시 요청 흐름을 역추적할 수 있습니다.
 * ThreadLocal을 사용하여 요청 단위로 컨텍스트를 관리합니다.
 */
public class TraceContextHolder {

    private static final int MAX_BREADCRUMBS = 50;

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAN_ID = new ThreadLocal<>();
    private static final ThreadLocal<TraceLevel> LEVEL = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORCE_TRACE = new ThreadLocal<>();
    private static final ThreadLocal<Long> REQUEST_START_MS = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<BreadcrumbEvent>> BREADCRUMBS = new ThreadLocal<>();

    private TraceContextHolder() {}

    /**
     * 컨텍스트를 초기화합니다.
     * @param traceId 추적 ID
     * @param spanId 스팬 ID
     * @param level 추적 레벨
     * @param forceTrace 강제 추적 여부
     */
    public static void init(String traceId, String spanId, TraceLevel level, boolean forceTrace) {
        TRACE_ID.set(traceId);
        SPAN_ID.set(spanId);
        LEVEL.set(level);
        FORCE_TRACE.set(forceTrace);
        REQUEST_START_MS.set(System.currentTimeMillis());
        BREADCRUMBS.set(new ArrayDeque<>(MAX_BREADCRUMBS + 1));
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
    public static TraceLevel level() {
        return LEVEL.get();
    }

    /**
     * 강제 추적 모드인지 확인합니다.
     */
    public static boolean isForceTrace() {
        return Boolean.TRUE.equals(FORCE_TRACE.get());
    }

    /**
     * 현재 상태가 TRACE 레벨이거나 강제 추적 모드인지 확인합니다.
     */
    public static boolean isTrace() {
        return level() == TraceLevel.TRACE || isForceTrace();
    }

    /**
     * 요청 처리 중 발생한 이벤트를 Breadcrumb으로 기록합니다.
     * 요청 컨텍스트 외부(배치 워커, 초기화 시점 등)에서 호출되면 무시합니다.
     *
     * @param type   이벤트 유형 (SQL, EXTERNAL_CALL, CACHE_HIT 등)
     * @param detail 이벤트 상세 설명
     */
    public static void addBreadcrumb(String type, String detail) {
        ArrayDeque<BreadcrumbEvent> crumbs = BREADCRUMBS.get();

        if (crumbs == null) {
            return;
        }

        Long start = REQUEST_START_MS.get();
        long offset = (start != null) ? System.currentTimeMillis() - start : 0;

        if (crumbs.size() >= MAX_BREADCRUMBS) {
            crumbs.pollFirst();
        }

        crumbs.addLast(new BreadcrumbEvent(offset, type, detail));
    }

    /**
     * 현재까지 기록된 Breadcrumb 이벤트 목록을 반환합니다.
     * 반환값은 방어 복사본이므로 원본에 영향을 주지 않습니다.
     */
    public static List<BreadcrumbEvent> getBreadcrumbs() {
        ArrayDeque<BreadcrumbEvent> crumbs = BREADCRUMBS.get();

        return (crumbs != null) ? new ArrayList<>(crumbs) : Collections.emptyList();
    }

    /**
     * 비동기 워커 스레드에서 부모 요청의 컨텍스트를 복원합니다.
     * 브레드크럼을 포함한 전체 상태를 복사하므로 init()과 달리 기존 이벤트가 보존됩니다.
     */
    public static void restore(String traceId, String spanId, TraceLevel level, boolean forceTrace,
                               List<BreadcrumbEvent> breadcrumbs) {
        TRACE_ID.set(traceId);
        SPAN_ID.set(spanId);
        LEVEL.set(level);
        FORCE_TRACE.set(forceTrace);
        REQUEST_START_MS.set(System.currentTimeMillis());
        BREADCRUMBS.set(new ArrayDeque<>(breadcrumbs));
    }

    /**
     * ThreadLocal에 저장된 컨텍스트 정보를 제거합니다.
     */
    public static void clear() {
        TRACE_ID.remove();
        SPAN_ID.remove();
        LEVEL.remove();
        FORCE_TRACE.remove();
        REQUEST_START_MS.remove();
        BREADCRUMBS.remove();
    }
}