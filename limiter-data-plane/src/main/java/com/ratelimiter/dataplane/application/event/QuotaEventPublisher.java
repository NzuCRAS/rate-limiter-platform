package com. ratelimiter. dataplane.application.event;

import com.ratelimiter.common.web.domain.event.QuotaConsumedEvent;
import lombok.extern.slf4j. Slf4j;
import org.springframework.beans.factory. annotation.Value;
import org. springframework.kafka.core.KafkaTemplate;
import org. springframework.kafka.support.SendResult;
import org. springframework.stereotype.Service;

import java.util. concurrent.CompletableFuture;

@Slf4j
@Service
public class QuotaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicName;

    public QuotaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${app.kafka.topic.quota-events: quota-events}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    /**
     * 异步发送配额消费事件
     */
    public void publishQuotaEvent(QuotaConsumedEvent event) {
        try {
            // 使用 tenantId 作为 partition key，保证同一租户的事件有序
            String partitionKey = event.getTenantId();

            log.info("=== Sending Kafka event:  eventId={}, requestId={}, topic={} ===",
                    event.getEventId(), event.getRequestId(), topicName);

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topicName, partitionKey, event);

            // 异步处理结果
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to send quota event.  EventId: {}, RequestId: {}, TenantId: {}",
                            event.getEventId(), event.getRequestId(), event.getTenantId(), throwable);
                } else {
                    log.debug("Successfully sent quota event. EventId: {}, RequestId: {}, Partition: {}, Offset: {}",
                            event.getEventId(), event.getRequestId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                }
            });

        } catch (Exception e) {
            log.error("Error publishing quota event.  EventId: {}, RequestId: {}",
                    event. getEventId(), event.getRequestId(), e);
        }
    }

    /**
     * 批量发送事件（如果需要）
     */
    public void publishQuotaEvents(java.util.List<QuotaConsumedEvent> events) {
        for (QuotaConsumedEvent event :  events) {
            publishQuotaEvent(event);
        }
    }
}