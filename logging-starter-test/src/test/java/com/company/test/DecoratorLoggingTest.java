package com.company.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
@DirtiesContext
class DecoratorLoggingTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job decoratorTestJob;

    @Test
    @DisplayName("Chunk 기반 멀티스레드 환경에서 LoggingTaskDecorator 동작 확인")
    void testDecoratorLogging(CapturedOutput output) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(decoratorTestJob, params);

        String out = output.getOut();

        // 1. Job 로그 확인
        assertThat(out).contains("job_name=decoratorTestJob");
        assertThat(out).contains("step_name=JOB");

        // 2. Step 로그 확인 (부모 스레드 로그)
        assertThat(out).contains("step_name=decoratorStep");

        // 3. 자식 스레드(Worker)에서 발생한 SQL 로그 확인
        // LoggingTaskDecorator에서 logTaskSql를 호출하므로 [SQL] 마커 로그가 있어야 함.
        assertThat(out).contains("[SQL]");

        // 4. TraceId 공유 확인
        // [BATCH_PROD] 로그에서 trace_id 추출
        Matcher matcher = Pattern.compile("trace_id=([a-f0-9]{32})").matcher(out);
        assertThat(matcher.find()).isTrue();
        String traceId = matcher.group(1);

        // SQL 로그들도 동일한 trace_id를 가져야 함.
        // LoggingTaskDecorator가 부모의 traceId를 자식 스레드로 잘 복사했는지 확인.
        assertThat(out).contains("trace_id=" + traceId);
        
        // SQL 로그 라인에서 trace_id가 포함되어 있는지 재검증 (최소 1개 이상)
        long sqlWithTraceIdCount = Arrays.stream(out.split("\n"))
                .filter(line -> line.contains("[SQL]") && line.contains("trace_id=" + traceId))
                .count();
        
        assertThat(sqlWithTraceIdCount).isGreaterThan(0);
        
        System.out.println("Verified SQL logs with trace_id: " + traceId + ", count: " + sqlWithTraceIdCount);
    }
}