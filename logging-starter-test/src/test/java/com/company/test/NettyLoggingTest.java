package com.company.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import com.company.test.config.TestNettyServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
@DirtiesContext
class NettyLoggingTest {

    @Autowired
    private TestNettyServerConfig nettyServerConfig;

    @Test
    @DisplayName("Netty TCP 로깅 테스트")
    void testNettyLogging(CapturedOutput output) throws Exception {
        int port = nettyServerConfig.getPort();
        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner in = new Scanner(socket.getInputStream())) {

            out.println("Hello Netty");
            if (in.hasNextLine()) {
                in.nextLine(); // Echo 응답 대기
            }
        }

        // 비동기 로깅을 위한 짧은 대기
        Thread.sleep(500);

        assertThat(output.getOut()).contains("[NETTY_PROD]");
        assertThat(output.getOut()).contains("Hello Netty");
        assertThat(output.getOut()).containsPattern("trace_id=[a-f0-9]{32}");
        assertThat(output.getOut()).containsPattern("span_id=[a-f0-9]{16}");
    }
}
