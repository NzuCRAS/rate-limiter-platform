package com.ratelimiter.accounting.config;

import org. apache.kafka.clients. consumer.ConsumerConfig;
import org. apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory. annotation.Value;
import org.springframework. context.annotation.Bean;
import org. springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core. DefaultKafkaConsumerFactory;
import org.springframework.kafka. listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org. springframework.kafka.support.serializer.JsonDeserializer;

import java.util. HashMap;
import java.util.Map;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.consumer. concurrency: 8}")
    private int concurrency;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        // 基础配置
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "accounting-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer. class);
        props.put(JsonDeserializer. TRUSTED_PACKAGES, "com.ratelimiter.common.web. domain.event");

        // 性能优化配置
        props. put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);        // 每次拉取1000条
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1048576);       // 最小1MB
        props. put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 300);         // 最大等待300ms
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 2097152); // 每分区最大2MB

        // 会话管理
        props. put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);      // 30秒会话超时
        props.put(ConsumerConfig. HEARTBEAT_INTERVAL_MS_CONFIG, 3000);    // 3秒心跳

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> batchKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setBatchListener(true);  // 启用批量监听
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 并发配置
        factory. setConcurrency(concurrency);

        // 批量处理配置
        factory.getContainerProperties().setPollTimeout(500);

        // 错误处理
        factory.setCommonErrorHandler(new DefaultErrorHandler());

        return factory;
    }
}