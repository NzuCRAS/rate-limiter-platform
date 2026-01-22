package com. ratelimiter. accounting.application.metrics;

import io.micrometer.core. instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype. Service;

import java.util.concurrent. TimeUnit;

@Slf4j
@Service
public class AuditMetricsService {

    private final MeterRegistry meterRegistry;

    public AuditMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录 Kafka 消息接收
     */
    public Timer.Sample startKafkaMessageProcessing(String tenantId, int batchSize) {
        meterRegistry.counter("kafka_message_received_total",
                "tenant_id", tenantId,
                "batch_size", String.valueOf(batchSize)).increment();

        return Timer.start(meterRegistry);
    }

    /**
     * 完成 Kafka 消息处理
     */
    public void finishKafkaMessageProcessing(Timer.Sample sample, boolean success, String tenantId,
                                             int batchSize, String errorType) {
        // 记录处理时长
        Timer timer = Timer.builder("kafka_message_processing_duration_seconds")
                .register(meterRegistry);
        sample.stop(timer);

        if (success) {
            meterRegistry. counter("kafka_message_processed_total",
                    "tenant_id", tenantId,
                    "batch_size", String. valueOf(batchSize)).increment();
        } else {
            meterRegistry.counter("kafka_message_failed_total",
                    "tenant_id", tenantId,
                    "error_type", errorType != null ? errorType :  "unknown").increment();
        }
    }

    /**
     * 记录审计记录保存
     */
    public void recordAuditRecordSaved(String tenantId, String resourceKey) {
        meterRegistry.counter("audit_record_saved_total",
                "tenant_id", tenantId).increment();
    }

    /**
     * 记录重复审计记录
     */
    public void recordAuditRecordDuplicate(String tenantId, String resourceKey, String requestId) {
        meterRegistry.counter("audit_record_duplicate_total",
                "tenant_id", tenantId).increment();

        log.debug("Duplicate audit record detected: tenantId={}, resourceKey={}, requestId={}",
                tenantId, resourceKey, requestId);
    }

    /**
     * 记录批处理操作
     */
    public void recordBatchProcessing(int batchSize, int savedCount, int duplicateCount, boolean success) {
        meterRegistry.counter("audit_batch_processed_total",
                "batch_size", String.valueOf(batchSize),
                "saved_count", String.valueOf(savedCount),
                "duplicate_count", String.valueOf(duplicateCount),
                "success", String.valueOf(success)).increment();

        if (success) {
            meterRegistry. counter("batch_insert_total").increment();
        } else {
            meterRegistry.counter("batch_insert_failed_total").increment();
        }
    }

    /**
     * 记录数据库保存延迟
     */
    public void recordAuditSaveLatency(long durationMs, int recordCount) {
        Timer.builder("audit_save_duration_seconds")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录数据库批量操作指标
     */
    public void recordDbBatchOperation(String operation, boolean success, long durationMs, int recordCount) {
        meterRegistry.counter("database_batch_operation_total",
                "operation", operation,
                "success", String. valueOf(success),
                "record_count", String. valueOf(recordCount)).increment();

        Timer.builder("database_batch_operation_duration_seconds")
                .register(meterRegistry)
                .record(durationMs, TimeUnit. MILLISECONDS);
    }

    /**
     * 记录审计表总记录数
     */
    public void recordTotalAuditRecords(long count) {
        meterRegistry.gauge("audit_total_records", count);
    }

    /**
     * 记录 Kafka 消费延迟
     */
    public void recordKafkaConsumerLag(String partition, long lag) {
        meterRegistry.gauge("kafka_consumer_lag",
                Tags.of("partition", partition), lag);
    }

    /**
     * 记录事件处理速率
     */
    public void recordEventProcessingRate(String tenantId, int eventsPerSecond) {
        meterRegistry.gauge("event_processing_rate",
                Tags.of("tenant_id", tenantId), eventsPerSecond);
    }

    /**
     * 增强的 Kafka 指标���控
     */
    public void recordKafkaPerformanceMetrics(int batchSize, long processingTimeMs, long kafkaLatencyMs) {
        // 批量大小分布
        meterRegistry. gauge("kafka_batch_size", batchSize);

        // 处理延迟
        Timer.builder("kafka_processing_latency")
                .register(meterRegistry)
                .record(processingTimeMs, TimeUnit.MILLISECONDS);

        // Kafka 网络延迟
        Timer.builder("kafka_network_latency")
                .register(meterRegistry)
                .record(kafkaLatencyMs, TimeUnit. MILLISECONDS);
    }
}