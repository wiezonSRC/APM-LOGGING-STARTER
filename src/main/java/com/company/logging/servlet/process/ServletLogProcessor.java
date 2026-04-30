package com.company.logging.servlet.process;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.enums.LogMarker;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.metrics.MetricsHolder;
import com.company.logging.core.process.AbstractLogProcessor;
import com.company.logging.core.support.util.CommonUtil;
import com.company.logging.core.support.util.SensitiveDataMasker;
import com.company.logging.servlet.context.LogApiContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;


public class ServletLogProcessor extends AbstractLogProcessor<LogApiContext> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 에러 코드로 간주할 JSON 키 목록
    private static final Set<String> ERROR_CODE_KEYS = Set.of(
            "resCode",
            "res_cd",
            "code"
    );

    public ServletLogProcessor(LoggingProperties properties) {
        super(properties);
    }

    @Override
    public void logApi(LogApiContext ctx) {
        String traceId = ctx.getTraceId();
        String spanId = ctx.getSpanId();
        TraceLevel level = resolveLevel();
        LoggingProperties.CaptureMode bodyMode = properties.getCapture().getBody();

        boolean isError = ctx.getEx() != null || hasErrorCode(ctx.getResponseBody());
        boolean isSlow = ctx.getElapsedMs() >= properties.getSlow().getApiMs();

        // 1. 기본 요약 로그 (항상 출력)
        logger.info(
                LogMarker.API_PROD.marker(),
                "trace_id={} span_id={} interface_id={} uri={} method={} status={} elapsed={}ms",
                traceId,
                spanId,
                ctx.getInterfaceId(),
                ctx.getUri(),
                ctx.getMethod(),
                ctx.getStatus(),
                ctx.getElapsedMs()
        );

        // 2. 바디 로깅 여부 결정
        boolean shouldLogBody = false;
        if (bodyMode == LoggingProperties.CaptureMode.ALWAYS) {
            shouldLogBody = true;
        } else if (bodyMode == LoggingProperties.CaptureMode.ERROR && isError) {
            shouldLogBody = true;
        } else if (bodyMode == LoggingProperties.CaptureMode.SLOW && isSlow) {
            shouldLogBody = true;
        } else if (bodyMode == LoggingProperties.CaptureMode.SAMPLE) {
            shouldLogBody = level == TraceLevel.TRACE;
        } else if (level == TraceLevel.TRACE) {
            shouldLogBody = true;
        }

        if (shouldLogBody || isError) {
            int maxBodyLen = properties.getLimit().getMaxBodyLength();

            // 로그 출력 직전에 민감정보 마스킹 적용 — 비즈니스 로직의 원본 데이터에는 영향 없음
            String reqBody = SensitiveDataMasker.mask(CommonUtil.truncate(ctx.getRequestBody(), maxBodyLen));
            String resBody = SensitiveDataMasker.mask(CommonUtil.truncate(ctx.getResponseBody(), maxBodyLen));
            
            LogMarker marker = isError ? LogMarker.EXCEPTION : LogMarker.API_TRACE;

            logger.info(
                    marker.marker(),
                    "trace_id={} span_id={} params={} request={} response={}",
                    traceId,
                    spanId,
                    ctx.getRequestParam(),
                    reqBody,
                    resBody
            );
        }

        MetricsHolder.recordApi(ctx.getMethod(), ctx.getUri(), ctx.getElapsedMs(), isError);

        logSqlDetails(traceId, spanId, level, isError);
        logException(ctx, traceId, spanId);
    }

    public boolean hasErrorCode(String body) {
        if (body == null || body.isEmpty()) return false;

        try {
            JsonNode root = objectMapper.readTree(body);
            return containsErrorCode(root);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsErrorCode(JsonNode node) {
        if (node.isObject()) {
            return containsErrorInObject(node);
        }

        if (node.isArray()) {
            return containsErrorInArray(node);
        }

        return false;
    }

    private boolean containsErrorInObject(JsonNode node) {
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();

            if (isErrorField(entry)) {
                return true;
            }

            if (containsErrorCode(entry.getValue())) {
                return true;
            }
        }

        return false;
    }

    private boolean containsErrorInArray(JsonNode node) {
        for (JsonNode child : node) {
            if (containsErrorCode(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean isErrorField(Map.Entry<String, JsonNode> entry) {
        return ERROR_CODE_KEYS.contains(entry.getKey())
                && "9999".equals(entry.getValue().asText());
    }
}
