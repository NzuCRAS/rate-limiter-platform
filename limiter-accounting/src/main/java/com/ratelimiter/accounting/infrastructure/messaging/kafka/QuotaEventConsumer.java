package com.ratelimiter.accounting.infrastructure.messaging.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.accounting. application.AuditService;
import com.ratelimiter.accounting.infrastructure.persistence.mysql.QuotaAuditEntity;
import com.ratelimiter.common.web.domain.event.QuotaConsumedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework. kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging. handler.annotation.Header;
import org.springframework.messaging.handler. annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class QuotaEventConsumer {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    // 批量处理缓存
    private final List<QuotaConsumedEvent> eventBuffer = new ArrayList<>();
    private final int BATCH_SIZE = 100;  // 批量大小
    private long lastFlushTime = System.currentTimeMillis();
    private final long FLUSH_INTERVAL_MS = 5000;  // 5秒强制刷新

    public QuotaEventConsumer(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

/*
    *//**
     * 消费配额事件
     *
     * @param event 配额消费事件
     * @param partition 分区号
     * @param offset 偏移量
     * @param ack 手动确认
     *//*
    @KafkaListener(
            topics = "${app.kafka.topic.quota-events:quota-events}",
            groupId = "accounting-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeQuotaEvent(@Payload QuotaConsumedEvent event,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment ack) {
        try {
            log.info("=== Received quota event: eventId={}, requestId={}, partition={}, offset={} ===",
                    event. getEventId(), event.getRequestId(), partition, offset);

            // 转换为审计实体
            QuotaAuditEntity auditEntity = convertToAuditEntity(event);

            // 使用 try-catch 捕获重复键异常，而不是预先查询
            try {
                boolean saved = auditService.save(auditEntity);
                if (saved) {
                    log. info("Successfully saved audit record:  eventId={}, requestId={}",
                            event.getEventId(), event.getRequestId());
                    ack.acknowledge();
                } else {
                    log.error("Failed to save audit record: eventId={}, requestId={}",
                            event.getEventId(), event.getRequestId());
                }
            } catch (Exception dbException) {
                // 检查是否是重复键异常
                if (dbException. getCause() instanceof java.sql.SQLIntegrityConstraintViolationException &&
                        dbException.getMessage().contains("Duplicate entry")) {

                    log.warn("Duplicate audit record detected for requestId: {}, treating as successful",
                            event.getRequestId());
                    ack.acknowledge(); // 重复记录也算处理成功
                } else {
                    log.error("Database error saving audit record: eventId={}, requestId={}",
                            event.getEventId(), event.getRequestId(), dbException);
                    // 不确认消息，会重试
                }
            }

        } catch (Exception e) {
            log.error("Error processing quota event: eventId={}, requestId={}",
                    event.getEventId(), event.getRequestId(), e);
        }
    }*/


    /**
     * 优化的批量消费版本
     *
     * 优势：
     * 1. 批量接收消息，减少 Kafka 网络开销
     * 2. 批量查询去重，减少数据库查询次数
     * 3. 批量插入，大幅提升数据库写入性能
     * 4. 统一异常处理消息确认
     */
    @KafkaListener(
            topics = "${app. kafka.topic.quota-events:quota-events}",
            groupId = "accounting-service",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumeQuotaEventsBatch(List<QuotaConsumedEvent> events,
                                        @Header(KafkaHeaders. RECEIVED_PARTITION) List<Integer> partitions,
                                        @Header(KafkaHeaders.OFFSET) List<Long> offsets,
                                        Acknowledgment ack) {
        try {
            log.info("=== Received batch of {} quota events from partitions: {} ===",
                    events.size(), partitions);

            if (events.isEmpty()) {
                ack.acknowledge();
                return;
            }

            // 1. 提取所有 requestId 用于批量去重查询
            List<String> requestIds = events.stream()
                    . map(QuotaConsumedEvent:: getRequestId)
                    .distinct()
                    .toList();

            log.debug("Checking {} unique requestIds for duplicates", requestIds. size());

            // 2. 批量查询已存在的 requestId
            List<String> existingRequestIds = auditService. lambdaQuery()
                    .select(QuotaAuditEntity::getRequestId)
                    .in(QuotaAuditEntity::getRequestId, requestIds)
                    .list()
                    .stream()
                    .map(QuotaAuditEntity::getRequestId)
                    .toList();

            log.debug("Found {} existing records", existingRequestIds. size());

            // 3. 过滤出需要插入的事件
            List<QuotaAuditEntity> auditEntities = events.stream()
                    .filter(event -> !existingRequestIds.contains(event. getRequestId()))
                    .map(this::convertToAuditEntity)
                    .toList();

            // 4. 批量插入
            if (!auditEntities. isEmpty()) {
                try {
                    boolean saved = auditService.saveBatch(auditEntities, 1000); // 每批1000条
                    if (saved) {
                        log.info("Successfully batch saved {} audit records (filtered {} duplicates)",
                                auditEntities.size(), events.size() - auditEntities.size());
                    } else {
                        log.error("Failed to batch save {} audit records", auditEntities.size());
                        return; // 不确认消息，会重试
                    }
                } catch (Exception dbException) {
                    log.error("Database error during batch save", dbException);

                    // 如果批量插入失败，可能是部分重复，尝试逐条插入
                    handleBatchInsertFailure(auditEntities);
                }
            } else {
                log.info("All {} events in batch already processed, skipping insert", events.size());
            }

            // 5. 确认所有消息处理完成
            ack. acknowledge();

            log.debug("Batch processing completed for {} events", events. size());

        } catch (Exception e) {
            log.error("Error processing quota events batch, count: {}", events.size(), e);
            // 不确认消息，Kafka 会重试
        }
    }

    /**
     * 处理批量插入失败的情况（可能是部分重复）
     */
    private void handleBatchInsertFailure(List<QuotaAuditEntity> entities) {
        log.warn("Batch insert failed, trying individual insert for {} records", entities.size());

        int successCount = 0;
        int duplicateCount = 0;

        for (QuotaAuditEntity entity : entities) {
            try {
                boolean saved = auditService. save(entity);
                if (saved) {
                    successCount++;
                } else {
                    log.warn("Failed to save individual record: requestId={}", entity.getRequestId());
                }
            } catch (Exception e) {
                if (e. getCause() instanceof java.sql.SQLIntegrityConstraintViolationException &&
                        e. getMessage().contains("Duplicate entry")) {
                    duplicateCount++;
                    log.debug("Duplicate record:  requestId={}", entity.getRequestId());
                } else {
                    log.error("Error saving individual record: requestId={}", entity.getRequestId(), e);
                    throw e; // 重新抛出非重复键异常
                }
            }
        }

        log.info("Individual insert completed: {} success, {} duplicates", successCount, duplicateCount);
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

/*        // 把 traceId 放到 client_ip 字段（或者你可以修改数据库添加 trace_id 字段）
        entity.setClientIp(event.getTraceId());
        // 把 processPath 放到 user_agent 字段
        entity.setUserAgent(event.getProcessPath());*/

        // 辅助字段
        entity.setEventId(event.getEventId());
        entity.setTraceId(event.getTraceId());

        return entity;
    }
}