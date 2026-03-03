package com.company.logging.core.support.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * 마커(Marker) 존재 여부에 따라 로그 기록 여부를 결정하는 필터입니다.
 * <p>
 * acceptIfMarkerPresent 가 true 이면 마커가 있는 경우만 ACCEPT, 없으면 DENY 합니다. (Metric 파일용)
 * acceptIfMarkerPresent 가 false 이면 마커가 있는 경우 DENY, 없으면 NEUTRAL 합니다. (일반 로그 파일용)
 */
public class MetricMarkerFilter extends Filter<ILoggingEvent> {

    private boolean acceptIfMarkerPresent = true;

    public void setAcceptIfMarkerPresent(boolean acceptIfMarkerPresent) {
        this.acceptIfMarkerPresent = acceptIfMarkerPresent;
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }

        Marker marker = event.getMarker();
        boolean hasMarker = (marker != null);

        if (acceptIfMarkerPresent) {
            // 마커가 있는 로그만 허용 (API_METRIC.log 전용)
            return hasMarker ? FilterReply.ACCEPT : FilterReply.DENY;
        } else {
            // 마커가 있는 로그는 거부 (일반 로그에서 메트릭 제외용)
            return hasMarker ? FilterReply.DENY : FilterReply.NEUTRAL;
        }
    }
}
