package com.ratelimiter.dataplane.config;

import org. springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org. springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Kafka 发布专用线程池
     */
    @Bean("kafkaPublishExecutor")
    public Executor kafkaPublishExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);      // 核心线程数
        executor.setMaxPoolSize(50);       // 最大线程数
        executor.setQueueCapacity(1000);   // 队列容量
        executor. setThreadNamePrefix("kafka-publish-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}