package com.company.logging.core.enums;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public enum LogMarker {
    API_PROD,
    API_DEBUG,
    API_TRACE,
    REQ_BODY,
    RES_BODY,
    NETTY_PROD,
    NETTY_DATA,
    BATCH_PROD,
    SQL,
    SQL_SLOW,
    SQL_EXCEPTION,
    EXCEPTION;

    private final Marker marker;

    LogMarker() {
        this.marker = MarkerFactory.getMarker(this.name());
    }

    public Marker marker() {
        return marker;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
