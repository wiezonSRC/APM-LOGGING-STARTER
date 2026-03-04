package com.company.test.config;

import com.company.logging.batch.listener.LoggingBatchListener;
import com.company.logging.batch.support.LoggingTaskDecorator;
import com.company.test.mapper.TestMapper;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;
import java.util.List;

@Configuration
public class DecoratorTestConfig {

    @Autowired
    private LoggingBatchListener loggingBatchListener;

    @Autowired
    private TestMapper testMapper;

    @Bean
    public Job decoratorTestJob(JobRepository jobRepository, Step decoratorStep) {
        return new JobBuilder("decoratorTestJob", jobRepository)
                .listener(loggingBatchListener)
                .start(decoratorStep)
                .build();
    }

    @Bean
    public Step decoratorStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setTaskDecorator(new LoggingTaskDecorator());

        return new StepBuilder("decoratorStep", jobRepository)
                .<Integer, Integer>chunk(1, transactionManager)
                .listener(loggingBatchListener)
                .reader(new ListItemReader<>(Arrays.asList(1, 2, 3)))
                .processor((ItemProcessor<Integer, Integer>) item -> {
                    // Check if traceId exists in child thread
                    testMapper.selectOne(); // Should produce SQL log with same traceId
                    return item;
                })
                .writer(items -> {
                    for (Integer item : items) {
                        testMapper.selectOne();
                    }
                })
                .taskExecutor(executor)
                .build();
    }
}
