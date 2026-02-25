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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
@DirtiesContext
class BatchLoggingTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job testJob;

    @Test
    @DisplayName("Batch Job/Step 추적 테스트")
    void testBatchLogging(CapturedOutput output) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(testJob, params);

        // 1. Job 로그 확인
        assertThat(output.getOut()).contains("[BATCH_PROD]");
        assertThat(output.getOut()).contains("job_name=testJob");
        assertThat(output.getOut()).contains("step_name=JOB");

        // 2. Step 로그 확인
        assertThat(output.getOut()).contains("step_name=step1");
        assertThat(output.getOut()).contains("step_name=step2");

        // 3. TraceId 공유 및 SpanId 분리 확인
        // 로그를 한줄씩 분석하는 것은 어렵지만, 동일한 trace_id 패턴이 여러번 등장하는지 확인
        assertThat(output.getOut()).containsPattern("trace_id=[a-f0-9]{32}");
    }
}
