package com.company.test.batch;

import com.company.logging.batch.listener.LoggingBatchListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.batch.core.job.SimpleJob;
import org.springframework.batch.core.step.AbstractStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamicJobRunner {

    @Autowired private JobLauncher jobLauncher;
    @Autowired private LoggingBatchListener loggingBatchListener;

    public void run(Job job) throws Exception {
        // 1. Job 레벨 리스너 동적 등록 (AbstractInitJob 로직 재현)
        if (job instanceof AbstractJob) {
            ((AbstractJob) job).registerJobExecutionListener(loggingBatchListener);
        }

        // 2. Step 레벨 리스너 동적 등록
        if (job instanceof SimpleJob) {
            SimpleJob simpleJob = (SimpleJob) job;
            for (String stepName : simpleJob.getStepNames()) {
                org.springframework.batch.core.Step step = simpleJob.getStep(stepName);
                if (step instanceof AbstractStep) {
                    ((AbstractStep) step).registerStepExecutionListener(loggingBatchListener);
                }
            }
        }

        // Job 실행
        JobParameters params = new JobParametersBuilder()
                .addLong("BATCH_UUID", System.currentTimeMillis())
                .toJobParameters();
        
        jobLauncher.run(job, params);
    }
}
