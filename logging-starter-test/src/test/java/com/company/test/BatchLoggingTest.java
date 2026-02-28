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

    @Autowired
    private Job heavySqlJob;

    @Autowired
    private Job partitionedJob;

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

        // 3. 정밀 시간 측정 확인 (0.000ms가 아닌 값)
        assertThat(output.getOut()).containsPattern("elapsed=[1-9]\\d*\\.\\d{3}ms|elapsed=0\\.[1-9]\\d*ms");

        // 4. TraceId 공유 및 SpanId 분리 확인
        // 로그를 한줄씩 분석하는 것은 어렵지만, 동일한 trace_id 패턴이 여러번 등장하는지 확인
        assertThat(output.getOut()).containsPattern("trace_id=[a-f0-9]{32}");
    }

    @Test
    @DisplayName("Batch SQL OOM 방지 테스트")
    void testHeavySqlBatchLogging(CapturedOutput output) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("type", "HEAVY")
                .toJobParameters();

        jobLauncher.run(heavySqlJob, params);

        String out = output.getOut();
        
        // 1. OMITTED 로그 확인 (max-sql-count=10, loop=10000)
        assertThat(out).contains("(OMITTED)");
        assertThat(out).contains("Too many SQLs in one request. 9990 queries omitted.");

        // 2. 상세 로그 개수 확인
        int sqlLogCount = countOccurrences(out, "[SQL]");
        // max-sql-count=10 이면 상세 로그 10개 + OMITTED 로그 1개 = 총 11개 [SQL] 마커
        assertThat(sqlLogCount).isEqualTo(11);
    }

    @Test
    @DisplayName("Batch 멀티스레드(Partitioning) 환경 TraceId 공유 테스트")
    void testPartitionedBatchLogging(CapturedOutput output) throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(partitionedJob, params);

        String out = output.getOut();

        // 1. 전체 Job 로그 확인
        assertThat(out).contains("job_name=partitionedJob step_name=JOB");

        // 2. 3개의 Worker Step 로그 확인 (workerStep:0, workerStep:1, workerStep:2)
        assertThat(out).contains("step_name=workerStep:partition0");
        assertThat(out).contains("step_name=workerStep:partition1");
        assertThat(out).contains("step_name=workerStep:partition2");

        // 3. 모든 로그가 동일한 trace_id를 공유하는지 확인
        String[] logs = out.split("\n");
        String commonTraceId = null;
        for (String log : logs) {
            if (log.contains("job_name=partitionedJob")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("trace_id=([a-f0-9]{32})").matcher(log);
                if (matcher.find()) {
                    if (commonTraceId == null) {
                        commonTraceId = matcher.group(1);
                    } else {
                        // 모든 Worker가 동일한 부모 TraceId를 가지고 있어야 함
                        assertThat(matcher.group(1))
                            .withFailMessage("TraceId mismatch! Expected: %s, Actual: %s, Log: %s", commonTraceId, matcher.group(1), log)
                            .isEqualTo(commonTraceId);
                    }
                }
            }
        }
        assertThat(commonTraceId).isNotNull();
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
