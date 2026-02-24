package com.company.logging.servlet.process;

import com.company.logging.core.config.LoggingProperties;
import com.company.logging.core.enums.LogMarker;
import com.company.logging.core.enums.TraceLevel;
import com.company.logging.core.process.AbstractLogProcessor;
import com.company.logging.servlet.context.LogApiContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;

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
        TraceLevel level = resolveLevel();

        boolean isError = ctx.getEx() != null || hasErrorCode(ctx.getResponseBody());

        // 1. 기본 요약 로그 (PROD 레벨)
        logger.info(
                "[{}] trace_id={} interface_id={} uri={} method={} status={} elapsed={}ms",
                LogMarker.API_PROD,
                traceId,
                ctx.getInterfaceId(),
                ctx.getUri(),
                ctx.getMethod(),
                ctx.getStatus(),
                ctx.getElapsedMs()
        );

        // 2. 상세 상세 로그 (DEBUG/TRACE 레벨이거나 에러일 때)
        if (level == TraceLevel.DEBUG || level == TraceLevel.TRACE || isError) {
            logger.info(
                    "[{}] trace_id={} params={} request={} response={}",
                    LogMarker.API_DEBUG,
                    traceId,
                    ctx.getRequestParam(),
                    ctx.getRequestBody(),
                    ctx.getResponseBody()
            );
        }

        logSqlDetails(traceId, level, isError);
        logException(ctx, traceId);
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
