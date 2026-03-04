package com.company.test.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DynamicJobConfig {

    @Bean
    public Job noListenerJob(JobRepository jobRepository, Step noListenerStep) {
        // 리스너를 명시적으로 등록하지 않음
        return new JobBuilder("noListenerJob", jobRepository)
                .start(noListenerStep)
                .build();
    }

    @Bean
    public Step noListenerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("noListenerStep", jobRepository)
                .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
                .build();
    }
}
