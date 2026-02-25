package com.company.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
@DirtiesContext
class ServletLoggingTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("SELECT 1 테스트")
    void testLogging(CapturedOutput output) {
        restTemplate.getForEntity("/test", String.class);
        assertThat(output.getOut()).contains("[API_PROD]");
        assertThat(output.getOut()).contains("uri=/test");
    }

    @Test
    @DisplayName("SELECT PARAM 테스트")
    void testLoggingWithParam(CapturedOutput output) {
        String paramValue = "testParamValue";
        restTemplate.getForEntity("/test-param?value={value}", String.class, paramValue);
        assertThat(output.getOut()).contains("value='" + paramValue + "'");
    }

    @Test
    @DisplayName("파일 업로드 테스트")
    void testFileUpload(CapturedOutput output) {
        String uniqueId = "upload-" + System.currentTimeMillis();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("test".getBytes()) {
            @Override
            public String getFilename() { return "test.txt"; }
        });
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        restTemplate.postForObject("/upload?uid=" + uniqueId, requestEntity, String.class);
        assertThat(output.getOut()).contains("/upload");
    }

    @Test
    @DisplayName("파일 다운로드 테스트")
    void testFileDownload(CapturedOutput output) {
        restTemplate.getForEntity("/download", byte[].class);
        assertThat(output.getOut()).contains("/download");
    }

    @Test
    @DisplayName("HTML 호출")
    void testHtml(){
        ResponseEntity<String> response = restTemplate.getForEntity("/html", String.class);
        assertThat(response.getBody()).contains("<html>");
    }

    @Test
    @DisplayName("SpanID 생성 및 traceparent 연동 테스트")
    void testSpanIdPropagation(CapturedOutput output) {
        restTemplate.getForEntity("/test?case=1", String.class);
        assertThat(output.getOut()).containsPattern("trace_id=[a-f0-9]{32}");
        assertThat(output.getOut()).containsPattern("span_id=[a-f0-9]{16}");

        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String parentSpanId = "00f067aa0ba902b7";
        String traceparent = "00-" + traceId + "-" + parentSpanId + "-01";

        HttpHeaders headers = new HttpHeaders();
        headers.set("traceparent", traceparent);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        restTemplate.exchange("/test?case=2", HttpMethod.GET, entity, String.class);

        assertThat(output.getOut()).contains("trace_id=" + traceId);
    }

    @Test
    @DisplayName("SQL 개수 제한(OOM 방지) 테스트")
    void testSqlLimit(CapturedOutput output) {
        restTemplate.getForEntity("/test-many-queries?count=15&test=sqlLimit", String.class);
        assertThat(output.getOut()).contains("(OMITTED)");
    }

    @Test
    @DisplayName("에러 쿼리 우선 로깅 테스트")
    void testErrorSqlPriority(CapturedOutput output) {
        restTemplate.getForEntity("/test-error-with-limit?count=13&test=errorPriority", String.class);
        assertThat(output.getOut()).contains("NON_EXISTENT_TABLE");
        assertThat(output.getOut()).contains("(OMITTED)");
    }

    @Test
    @DisplayName("SQL 길이 제한(Truncate) 테스트")
    void testSqlTruncate(CapturedOutput output) {
        restTemplate.getForEntity("/test-truncate", String.class);
        assertThat(output.getOut()).contains("Long text to test truncation");
    }
}
