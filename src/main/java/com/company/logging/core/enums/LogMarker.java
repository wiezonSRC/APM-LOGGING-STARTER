package com.company.logging.core.enums;

public enum LogMarker {
    API_PROD("API_PROD"),
    API_DEBUG("API_DEBUG]"),
    API_TRACE("API_TRACE"),
    REQ_BODY("REQ_BODY"),
    RES_BODY("RES_BODY"),
    NETTY_PROD("NETTY_PROD"),
    NETTY_DATA("NETTY_DATA"),
    BATCH_PROD("BATCH_PROD"),
    SQL("SQL"),
    SQL_SLOW("SQL_SLOW"),
    SQL_EXCEPTION("SQL_EXCEPTION"),
    EXCEPTION("EXCEPTION");

    private final String label;

    LogMarker(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
