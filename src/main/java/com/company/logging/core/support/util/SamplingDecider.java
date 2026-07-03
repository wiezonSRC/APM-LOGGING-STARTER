package com.company.logging.core.support.util;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.enums.TraceLevel;

import java.util.concurrent.ThreadLocalRandom;

public final class SamplingDecider {

    private SamplingDecider() {}

    /**
     * 샘플링 기반 forceTrace 여부를 결정합니다.
     * alreadyForced가 true면 그대로 반환하고, PROD 레벨일 때만 sampleRate를 적용합니다.
     */
    public static boolean shouldForceTrace(LoggingProperties props, boolean alreadyForced) {
        if (alreadyForced) {
            return true;
        }

        if (props.getTrace().getLevel() != TraceLevel.PROD) {
            return false;
        }

        double sampleRate = props.getCapture().getSampleRate();

        return sampleRate > 0 && ThreadLocalRandom.current().nextDouble() < sampleRate;
    }
}
