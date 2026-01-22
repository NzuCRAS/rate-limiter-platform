package com.ratelimiter.dataplane.application.event;

import com.ratelimiter.common.web.domain.event.QuotaConsumedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans. factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class QuotaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicName;

    // 分区策略
    private final AtomicLong roundRobinCounter = new AtomicLong(0);
    private final String partitionStrategy;

    // 性能统计
    private final AtomicLong totalSent = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final Map<Integer, AtomicLong> partitionMessageCount = new ConcurrentHashMap<>();

    public QuotaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${app.kafka.topic.quota-events:quota-events}") String topicName,
                               @Value("${app.kafka.partition-strategy:hybrid}") String partitionStrategy) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
        this.partitionStrategy = partitionStrategy;

        log.info("QuotaEventPublisher initialized: topic={}, partitionStrategy={}",
                topicName, partitionStrategy);
        startPerformanceLogging();
    }

    /**
     * 发送事件 - 多种分区策略
     */
    public void publishQuotaEvent(QuotaConsumedEvent event) {
        long startTime = System.currentTimeMillis();

        try {
            // 根据策略选择分区键
            String partitionKey = buildPartitionKey(event);

            kafkaTemplate.send(topicName, partitionKey, event)
                    .whenComplete((result, throwable) -> {
                        long latency = System.currentTimeMillis() - startTime;

                        if (throwable != null) {
                            log.error("Failed to send quota event: eventId={}, partitionKey={}, latency={}ms",
                                    event.getEventId(), partitionKey, latency, throwable);
                            totalFailed. incrementAndGet();
                        } else {
                            int partition = result.getRecordMetadata().partition();
                            partitionMessageCount.computeIfAbsent(partition, k -> new AtomicLong(0)).incrementAndGet();
                            totalSent.incrementAndGet();

                            log.debug("Sent event: eventId={}, partitionKey={}, partition={}, offset={}, latency={}ms",
                                    event.getEventId(), partitionKey, partition,
                                    result. getRecordMetadata().offset(), latency);
                        }
                    });

        } catch (Exception e) {
            long latency = System. currentTimeMillis() - startTime;
            log.error("Error publishing quota event: eventId={}, latency={}ms",
                    event. getEventId(), latency, e);
            totalFailed. incrementAndGet();
        }
    }

    /**
     * 多种分区策略
     */
    private String buildPartitionKey(QuotaConsumedEvent event) {
        return switch (partitionStrategy.toLowerCase()) {
            case "round_robin" ->
                // 策略1：轮询 - 最均匀
                    String.valueOf(roundRobinCounter.getAndIncrement());
            case "tenant_hash" ->
                // 策略2：租户哈希 - 保持租户数据局部性
                    event.getTenantId();
            case "resource_hash" ->
                // 策略3：资源哈希 - 按API分组
                    event.getResourceKey();
            default ->
                // 策略4：混合策略 - 平衡均匀性和局部性
                    buildHybridPartitionKey(event);
        };
    }

    /**
     * 混合分区策略 - 推荐
     */
    private String buildHybridPartitionKey(QuotaConsumedEvent event) {
        // 组合多个字段，增加分散性
        String tenantId = event. getTenantId();
        String resourceKey = event.getResourceKey();

        // 使用时间戳的低位增加随机性
        long timeSlice = System.currentTimeMillis() / 1000; // 每秒切换

        // 构造复合键
        return String. format("%s-%s-%d",
                tenantId.hashCode() % 10,           // 租户哈希取模
                resourceKey.hashCode() % 10,        // 资源哈希取模
                timeSlice % 10                      // 时间切片
        );
    }

    /**
     * 增强的性能统计
     */
    private void logPerformanceStats() {
        long sent = totalSent.get();
        long failed = totalFailed.get();

        if (sent > 0 || failed > 0) {
            // 分区分布统计
            StringBuilder partitionStats = new StringBuilder();
            long totalMessages = partitionMessageCount.values().stream()
                    .mapToLong(AtomicLong:: get).sum();

            partitionMessageCount.entrySet().stream()
                    .sorted(Map.Entry. comparingByKey())
                    .forEach(entry -> {
                        int partition = entry.getKey();
                        long count = entry.getValue().get();
                        double percentage = totalMessages > 0 ? (count * 100.0 / totalMessages) : 0;
                        partitionStats.append(String.format(" P%d:%d(%.1f%%)", partition, count, percentage));
                    });

            // 分区均匀度计算（标准差）
            double avgMessagesPerPartition = totalMessages / (double) partitionMessageCount.size();
            double variance = partitionMessageCount.values().stream()
                    .mapToDouble(count -> Math.pow(count.get() - avgMessagesPerPartition, 2))
                    .average().orElse(0.0);
            double stdDev = Math.sqrt(variance);
            double uniformityScore = 100 - (stdDev / avgMessagesPerPartition * 100);

            log.info("Kafka Performance: sent={}, failed={}, successRate={}%, uniformity={}%, partitions:[{}]",
                    sent, failed,
                    sent + failed > 0 ? (sent * 100 / (sent + failed)) : 100,
                    uniformityScore,
                    partitionStats);

            // 重置统计
            totalSent.set(0);
            totalFailed.set(0);
            partitionMessageCount. clear();
        }
    }

    private void startPerformanceLogging() {
        ScheduledExecutorService scheduler = Executors. newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::logPerformanceStats, 10, 10, TimeUnit.SECONDS);
    }
}