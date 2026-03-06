package com.company.test.config;

import com.company.logging.batch.listener.LoggingBatchListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import com.company.test.mapper.TestMapper;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class TestBatchConfig {

    @Autowired
    private LoggingBatchListener loggingBatchListener;
    private final TestMapper testMapper;

    public TestBatchConfig(TestMapper testMapper) {
        this.testMapper = testMapper;
    }

    @Bean
    public Job testJob(JobRepository jobRepository, Step step1, Step step2) {
        return new JobBuilder("testJob", jobRepository)
                .listener(loggingBatchListener)
                .start(step1)
                .next(step2)
                .build();
    }

    @Bean
    public Job heavySqlJob(JobRepository jobRepository, Step heavySqlStep) {
        return new JobBuilder("heavySqlJob", jobRepository)
                .listener(loggingBatchListener)
                .start(heavySqlStep)
                .build();
    }

    @Bean
    public Job partitionedJob(JobRepository jobRepository, Step masterStep) {
        return new JobBuilder("partitionedJob", jobRepository)
                .listener(loggingBatchListener)
                .start(masterStep)
                .build();
    }

    @Bean
    public Job asyncChunkJob(JobRepository jobRepository, Step asyncChunkStep) {
        return new JobBuilder("asyncChunkJob", jobRepository)
                .listener(loggingBatchListener)
                .start(asyncChunkStep)
                .build();
    }

    @Bean
    public Step asyncChunkStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setTaskDecorator(new com.company.logging.batch.support.LoggingTaskDecorator());
        
        return new StepBuilder("asyncChunkStep", jobRepository)
                .listener(loggingBatchListener)
                .<String, String>chunk(1, transactionManager)
                .reader(new org.springframework.batch.item.ItemReader<String>() {
                    private boolean read = false;
                    @Override
                    public String read() {
                        if (!read) {
                            read = true;
                            return "data";
                        }
                        return null;
                    }
                })
                .processor(item -> {
                    testMapper.selectOne();
                    return item;
                })
                .writer(items -> {})
                .taskExecutor(executor)
                .build();
    }

    @Bean
    public Step masterStep(JobRepository jobRepository, Step workerStep) {
        return new StepBuilder("masterStep", jobRepository)
                .partitioner(workerStep.getName(), partitioner())
                .step(workerStep)
                .gridSize(3)
                .taskExecutor(new SimpleAsyncTaskExecutor())
                .build();
    }

    @Bean
    public Partitioner partitioner() {
        return gridSize -> {
            Map<String, ExecutionContext> map = new HashMap<>();
            for (int i = 0; i < gridSize; i++) {
                map.put("partition" + i, new ExecutionContext());
            }
            return map;
        };
    }

    @Bean
    public Step step1(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step1", jobRepository)
                .listener(loggingBatchListener)
                .tasklet((contribution, chunkContext) -> {
                    testMapper.selectOne();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step step2(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("step2", jobRepository)
                .listener(loggingBatchListener)
                .tasklet((contribution, chunkContext) -> {
                    testMapper.selectOne();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step workerStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("workerStep", jobRepository)
                .listener(loggingBatchListener)
                .tasklet((contribution, chunkContext) -> {
                    testMapper.selectOne();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step heavySqlStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("heavySqlStep", jobRepository)
                .listener(loggingBatchListener)
                .tasklet((contribution, chunkContext) -> {
                    // max-sql-count is 10 in application-test.properties
                    for (int i = 0; i < 10000; i++) {
                        testMapper.selectOne();
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
