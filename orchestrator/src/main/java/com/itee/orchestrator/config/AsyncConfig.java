package com.itee.orchestrator.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.itee.orchestrator.ai.AiEngineProperties;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AiEngineProperties.class)
public class AsyncConfig {

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    /** Cap concurrent Azure calls — unbounded @Async was stampeding the AI engine. */
    @Bean(name = "taskExecutor")
    Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("itee-analyze-");
        executor.initialize();
        return executor;
    }
}
