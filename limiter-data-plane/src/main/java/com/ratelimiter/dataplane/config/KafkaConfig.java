package com.ratelimiter.dataplane.config;

import org.apache.kafka.clients. admin.NewTopic;
import org. springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation. Bean;
import org.springframework.context. annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic.quota-events:quota-events}")
    private String quotaEventsTopic;

    /**
     * 自动创建 quota-events topic
     */
    @Bean
    public NewTopic quotaEventsTopic() {
        return TopicBuilder.name(quotaEventsTopic)
                .partitions(3)          // 3个分区，支持并发消费
                .replicas(1)            // 单机环境用1个副本
                .build();
    }
}