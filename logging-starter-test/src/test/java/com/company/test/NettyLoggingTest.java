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
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
                in.nextLine();
            }
        }

        // 비동기 로깅을 위한 짧은 대기
        Thread.sleep(500);

        assertThat(output.getOut()).contains("[NETTY_PROD]");
        assertThat(output.getOut()).contains("Hello Netty");
        assertThat(output.getOut()).containsPattern("trace_id=[a-f0-9]{32}");
    }

    @Test
    @DisplayName("Netty TCP 동시성 로깅 테스트")
    void testNettyConcurrentLogging(CapturedOutput output) throws Exception {
        int port = nettyServerConfig.getPort();
        int concurrentCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try (Socket socket = new Socket("localhost", port);
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     Scanner in = new Scanner(socket.getInputStream())) {

                    out.println("ConcurrentMsg-" + index);
                    if (in.hasNextLine()) {
                        in.nextLine();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // 모든 로그가 처리될 때까지 대기
        Thread.sleep(2000);

        String out = output.getOut();
        for (int i = 0; i < concurrentCount; i++) {
            String msg = "ConcurrentMsg-" + i;
            assertThat(out).contains(msg);
            
            // 각 요청에 대해 SQL이 실행되었는지 확인
            assertThat(out).contains("Netty-" + msg);
        }

        // 전체 NETTY_PROD 로그 개수가 최소 concurrentCount만큼 있는지 확인
        int nettyLogCount = countOccurrences(out, "[NETTY_PROD]");
        assertThat(nettyLogCount).isGreaterThanOrEqualTo(concurrentCount);
    }

    @Test
    @DisplayName("Netty TCP 바디 길이 제한(Truncate) 테스트")
    void testNettyBodyTruncate(CapturedOutput output) throws Exception {
        int port = nettyServerConfig.getPort();
        String longMessage = "This is a very long message that should be truncated by the logging starter because it exceeds twenty characters";
        
        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner in = new Scanner(socket.getInputStream())) {

            out.println(longMessage);
            if (in.hasNextLine()) {
                in.nextLine();
            }
        }

        Thread.sleep(500);

        // log.limit.max-body-length=20 설정이 적용되었는지 확인
        assertThat(output.getOut()).contains("...(TRUNCATED)");
        // 20자 전후로 잘렸는지 확인
        assertThat(output.getOut()).contains("This is a very long ");
    }

    @Test
    @DisplayName("Netty TCP 데이터 조각(Fragmentation) 누적 로깅 테스트")
    void testNettyFragmentation(CapturedOutput output) throws Exception {
        int port = nettyServerConfig.getPort();
        
        try (Socket socket = new Socket("localhost", port);
             java.io.OutputStream out = socket.getOutputStream();
             Scanner in = new Scanner(socket.getInputStream())) {

            // 데이터를 의도적으로 쪼개서 전송
            out.write("Hello ".getBytes());
            out.flush();
            Thread.sleep(100);
            
            out.write("Netty ".getBytes());
            out.flush();
            Thread.sleep(100);
            
            out.write("World\n".getBytes());
            out.flush();

            if (in.hasNextLine()) {
                in.nextLine();
            }
        }

        Thread.sleep(500);

        // 조각난 데이터들이 하나로 합쳐져서 로그에 찍혔는지 확인
        assertThat(output.getOut()).contains("Hello Netty World");
    }

    @Test
    @DisplayName("Netty TCP SQL OOM 방지 테스트")
    void testNettySqlOomPrevention(CapturedOutput output) throws Exception {
        int port = nettyServerConfig.getPort();
        
        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner in = new Scanner(socket.getInputStream())) {

            out.println("OOM_TEST");
            if (in.hasNextLine()) {
                in.nextLine();
            }
        }

        Thread.sleep(500);

        // log.limit.max-sql-count=10 설정에 따라 (OMITTED) 메시지가 로그에 찍혔는지 확인
        assertThat(output.getOut()).contains("(OMITTED)");
    }

    @Test
    @DisplayName("Netty TCP 예외 로깅 테스트")
    void testNettyExceptionLogging(CapturedOutput output) throws Exception {
        int port = nettyServerConfig.getPort();
        
        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner in = new Scanner(socket.getInputStream())) {

            out.println("ERROR_TEST");
            // 서버 측에서 exceptionCaught가 발생할 수 있도록 잠시 대기
            Thread.sleep(500);
        }

        Thread.sleep(1000);

        String logOutput = output.getOut();
        if (!logOutput.contains("[EXCEPTION]")) {
             System.err.println("ACTUAL LOG OUTPUT FOR EXCEPTION TEST:\n" + logOutput);
        }
        
        // 1. 정상 로그 마커 확인
        assertThat(logOutput).contains("[NETTY_PROD]");
        // 2. Exception 마커 및 메시지 확인
        assertThat(logOutput).contains("[EXCEPTION]");
        assertThat(logOutput).contains("Netty Test Exception");
    }

    private int countOccurrences(String text, String match) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(match, index)) != -1) {
            count++;
            index += match.length();
        }
        return count;
    }
}
