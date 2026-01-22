package com.ratelimiter.accounting.infrastructure.messaging. kafka;

import com.fasterxml.jackson.databind. ObjectMapper;
import com.ratelimiter.accounting.application. AuditService;
import com.ratelimiter.accounting.application.metrics. AuditMetricsService;
import com.ratelimiter.accounting.infrastructure.persistence.mysql.QuotaAuditEntity;
import com.ratelimiter.common.web.domain.event.QuotaConsumedEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework. kafka.annotation.KafkaListener;
import org.springframework.kafka.support. Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging. handler.annotation.Header;
import org.springframework. stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class QuotaEventConsumer {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final AuditMetricsService metricsService;

    public QuotaEventConsumer(AuditService auditService, ObjectMapper objectMapper, AuditMetricsService metricsService) {
        this.auditService = auditService;
        this. objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    /**
     * 优化的批量消费版本
     */
    @KafkaListener(
            topics = "${app.kafka.topic.quota-events:quota-events}",
            groupId = "accounting-service",
            containerFactory = "batchKafkaListenerContainerFactory",
            concurrency = "3"
    )
    public void consumeQuotaEventsBatch(List<QuotaConsumedEvent> events,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
                                        @Header(KafkaHeaders.OFFSET) List<Long> offsets,
                                        Acknowledgment ack) {
        Timer.Sample sample = null;
        String tenantId = "mixed";

        try {
            // 统计各分区消息数量
            Map<Integer, Long> partitionCounts = partitions.stream()
                    .collect(Collectors.groupingBy(p -> p, Collectors.counting()));

            log.info("=== Received batch:  {} events from partitions:  {} ===",
                    events.size(), partitionCounts);

            if (events.isEmpty()) {
                ack.acknowledge();
                return;
            }

            // 按租户统计
            Map<String, Long> tenantCounts = events. stream()
                    .collect(Collectors.groupingBy(QuotaConsumedEvent::getTenantId, Collectors.counting()));

            log.debug("Tenant distribution: {}", tenantCounts);

            // 开始计时（使用混合作为统计）
            sample = metricsService.startKafkaMessageProcessing(tenantId, events. size());

            // 1. 提取所有 requestId 用于批量去重查询
            List<String> requestIds = events.stream()
                    .map(QuotaConsumedEvent::getRequestId)
                    .distinct()
                    .toList();

            log.debug("Checking {} unique requestIds for duplicates", requestIds.size());

            // 2. 批量查询已存在的 requestId
            long dbStartTime = System.currentTimeMillis();
            List<String> existingRequestIds = auditService.lambdaQuery()
                    . select(QuotaAuditEntity::getRequestId)
                    .in(QuotaAuditEntity::getRequestId, requestIds)
                    .list()
                    . stream()
                    .map(QuotaAuditEntity:: getRequestId)
                    . toList();

            long dbDuration = System.currentTimeMillis() - dbStartTime;
            metricsService.recordAuditSaveLatency(dbDuration, requestIds.size());

            log.debug("Found {} existing records", existingRequestIds.size());

            // 3. 过滤出需要插入的事件
            List<QuotaAuditEntity> auditEntities = new ArrayList<>();
            int duplicateCount = 0;

            for (QuotaConsumedEvent event : events) {
                if (existingRequestIds.contains(event.getRequestId())) {
                    duplicateCount++;
                    metricsService.recordAuditRecordDuplicate(
                            event.getTenantId(), event.getResourceKey(), event.getRequestId());
                } else {
                    auditEntities.add(convertToAuditEntity(event));
                }
            }

            // 4. 批量插入
            boolean success = true;
            if (! auditEntities.isEmpty()) {
                try {
                    long insertStartTime = System.currentTimeMillis();
                    boolean saved = auditService.saveBatch(auditEntities, 1000);
                    long insertDuration = System.currentTimeMillis() - insertStartTime;

                    metricsService.recordDbBatchOperation("audit_batch_insert", saved, insertDuration, auditEntities.size());

                    if (saved) {
                        log.info("Successfully batch saved {} audit records (filtered {} duplicates)",
                                auditEntities.size(), duplicateCount);

                        // 记录每个保存的记录
                        for (QuotaAuditEntity entity : auditEntities) {
                            metricsService.recordAuditRecordSaved(entity.getTenantId(), entity.getResourceKey());
                        }
                    } else {
                        success = false;
                        log. error("Failed to batch save {} audit records", auditEntities. size());
                    }
                } catch (Exception dbException) {
                    success = false;
                    log.error("Database error during batch save", dbException);
                    handleBatchInsertFailure(auditEntities);
                }
            } else {
                log.info("All {} events in batch already processed, skipping insert", events.size());
            }

            // 5. 记录批处理指标
            metricsService. recordBatchProcessing(events. size(), auditEntities.size(), duplicateCount, success);

            if (success) {
                ack.acknowledge();
            }

            // 6. 完成计时
            if (sample != null) {
                metricsService.finishKafkaMessageProcessing(sample, success, tenantId, events.size(), null);
            }

            log.debug("Batch processing completed for {} events", events. size());

        } catch (Exception e) {
            log.error("Error processing quota events batch, count:  {}", events.size(), e);
            if (sample != null) {
                metricsService.finishKafkaMessageProcessing(sample, false, tenantId, events.size(),
                        e.getClass().getSimpleName());
            }
        }
    }

    /**
     * 处理批量插入失败的情况（可能是部分重复）
     */
    private void handleBatchInsertFailure(List<QuotaAuditEntity> entities) {
        log.warn("Batch insert failed, trying individual insert for {} records", entities.size());

        int successCount = 0;
        int duplicateCount = 0;
        long startTime = System.currentTimeMillis();

        for (QuotaAuditEntity entity : entities) {
            try {
                boolean saved = auditService.save(entity);
                if (saved) {
                    successCount++;
                    metricsService.recordAuditRecordSaved(entity. getTenantId(), entity.getResourceKey());
                } else {
                    log.warn("Failed to save individual record: requestId={}", entity.getRequestId());
                }
            } catch (Exception e) {
                if (e. getCause() instanceof java.sql.SQLIntegrityConstraintViolationException &&
                        e.getMessage().contains("Duplicate entry")) {
                    duplicateCount++;
                    metricsService.recordAuditRecordDuplicate(
                            entity.getTenantId(), entity.getResourceKey(), entity.getRequestId());
                    log.debug("Duplicate record: requestId={}", entity.getRequestId());
                } else {
                    log. error("Error saving individual record:  requestId={}", entity.getRequestId(), e);
                    throw e;
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        metricsService.recordDbBatchOperation("audit_individual_insert_fallback", true, duration, entities. size());
        log.info("Individual insert completed:  {} success, {} duplicates", successCount, duplicateCount);
    }

    /**
     * 转换事件为审计实体
     */
    private QuotaAuditEntity convertToAuditEntity(QuotaConsumedEvent event) {
        QuotaAuditEntity entity = new QuotaAuditEntity();

        entity.setRequestId(event.getRequestId());
        entity.setTenantId(event.getTenantId());
        entity.setResourceKey(event.getResourceKey());
        entity.setTokens(event.getTokensRequested());
        entity.setAllowed(event.getAllowed());
        entity.setRemaining(event.getRemainingTokens());
        entity.setReason(event.getReason());
        entity.setPolicyVersion(event.getPolicyVersion());
        entity.setLatencyMs(event.getProcessTimeMs() != null ? event.getProcessTimeMs().intValue() : null);
        entity.setTimestamp(event.getTimestamp());

        // 辅助字段
        entity.setEventId(event.getEventId());
        entity.setTraceId(event.getTraceId());

        return entity;
    }
}