package com.company.logging.enums;

public enum LogMarker {
    API_PROD("[API_PROD]"),
    REQ_BODY("[REQ_BODY]"),
    RES_BODY("[RES_BODY]"),
    NETTY_PROD("[NETTY_PROD]"),
    NETTY_DAYA("[NETTY_DAYA]"),
    BATCH_PROD("[BATCH_PROD]"),
    SQL("[SQL]"),
    SLOW_SQL("[SLOW_SQL]"),
    EXCEPTION("[EXCEPTION]");

    private final String label;

    LogMarker(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
