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
import com.company.test.mapper.TestMapper;

@Configuration
public class TestBatchConfig {

    private final TestMapper testMapper;

    public TestBatchConfig(TestMapper testMapper) {
        this.testMapper = testMapper;
    }

    @Bean
    public Job testJob(JobRepository jobRepository, Step step1, Step step2) {
        return new JobBuilder("testJob", jobRepository)
                .start(step1)
                .next(step2)
                .build();
    }

    @Bean
    public Step step1(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step1", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    testMapper.selectOne();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step step2(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step2", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    testMapper.selectOne();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
