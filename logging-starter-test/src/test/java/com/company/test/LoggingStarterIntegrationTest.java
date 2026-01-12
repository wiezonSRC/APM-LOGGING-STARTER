package com.company.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;

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
        String response = restTemplate.getForObject("/test", String.class);

        // Assert Response
        assertThat(response).contains("Result: 1");

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
        String response = restTemplate.getForObject("/test-param?value=" + paramValue, String.class);

        // Assert Response
        assertThat(response).contains("Result with param: " + paramValue);

        // Assert Logs
        assertThat(output.getOut()).contains("[API_PROD]");
        assertThat(output.getOut()).contains("uri=/test-param");

        // Verify SQL logging (Interceptor)
        assertThat(output.getOut()).contains("[API_PROD] [SQL]");
        // Verify that the parameter is logged. 
        // Based on SqlTraceInterceptor logic: {value='testParamValue'}
        assertThat(output.getOut()).contains("value='" + paramValue + "'");
    }
}
