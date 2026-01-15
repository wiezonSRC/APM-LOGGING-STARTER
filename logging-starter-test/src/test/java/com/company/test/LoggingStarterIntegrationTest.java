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
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class LoggingStarterIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("SELECT 1 테스트")
    void testLogging(CapturedOutput output) {
        // Act
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/test",
                HttpMethod.GET,
                entity,
                String.class
        );

        String body= response.getBody();
        // Assert Response
        assertThat(body).contains("Result: 1");

        // Assert Logs
        assertThat(output.getOut()).contains("[API_PROD]");
        assertThat(output.getOut()).contains("uri=/test");
        
        // Verify SQL logging (Interceptor)
        assertThat(output.getOut()).contains("[API_PROD] [SQL]");
//        assertThat(output.getOut()).contains("SELECT 1");
    }

    @Test
    @DisplayName("SELECT PARAM 테스트")
    void testLoggingWithParam(CapturedOutput output) {
        // Act
        String paramValue = "testParamValue";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/test-param?value={value}",
                HttpMethod.GET,
                entity,
                String.class,
                paramValue
        );

        String body = response.getBody();

        // Assert Response
        assertThat(body).contains("Result with param: " + paramValue);

        // Assert Logs
        assertThat(output.getOut()).contains("[API_PROD]");
        assertThat(output.getOut()).contains("uri=/test-param");

        // Verify SQL logging (Interceptor)
        assertThat(output.getOut()).contains("[API_PROD] [SQL]");
        // Verify that the parameter is logged. 
        // Based on SqlTraceInterceptor logic: {value='testParamValue'}
        assertThat(output.getOut()).contains("value='" + paramValue + "'");
    }

    @Test
    @DisplayName("파일 업로드 테스트")
    void testFileUpload(CapturedOutput output) {
        // Arrange
        String fileName = "test-file.txt";
        String content = "Hello, World!";
        ByteArrayResource resource = new ByteArrayResource(content.getBytes()) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Act
        String response = restTemplate.postForObject("/upload", requestEntity, String.class);

        // Assert Response
        assertThat(response).contains("File uploaded: " + fileName);
        assertThat(response).contains("size: " + content.length());

        // Assert Logs
        assertThat(output.getOut()).contains("[API_PROD]");
        assertThat(output.getOut()).contains("uri=/upload");
    }

    @Test
    @DisplayName("파일 다운로드 테스트")
    void testFileDownload(CapturedOutput output) {

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM));

        HttpEntity<Void> entity = new HttpEntity<>(headers);



        // Act
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/download",
                HttpMethod.GET,
                entity,
                byte[].class
        );

        // Assert Response
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);

        assertThat(response.getHeaders()
                .getContentDisposition()
                .getFilename())
                .isEqualTo("download.txt");

        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(body).isEqualTo("This is a downloadable file content.");

        // Assert Logs (메타 로그만)
        assertThat(output.getOut()).contains("[API_PROD]");
        assertThat(output.getOut()).contains("uri=/download");
    }
}
