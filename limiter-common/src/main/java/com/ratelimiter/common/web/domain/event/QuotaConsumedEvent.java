package com.ratelimiter.common.web.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time. Instant;
import java.util.Map;
import java.util. UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuotaConsumedEvent {

    private String eventId;           // 事件唯一ID
    private String requestId;         // 业务请求ID
    private String tenantId;          // 租户ID
    private String resourceKey;       // 资源键
    private Long tokensRequested;     // 请求的token数量
    private Long tokensConsumed;      // 实际消费的token数量
    private Boolean allowed;          // 是否允许
    private String reason;            // 拒绝原因（空表示允许）
    private String policyVersion;     // 策略版本
    private Long remainingTokens;     // 剩余token数量
    private Long timestamp;           // 事件时间戳
    private String traceId;           // 链路追踪ID

    // 扩展信息
    private Map<String, Object> metadata;

    // 性能指标
    private Long processTimeMs;       // 处理耗时（毫秒）
    private String processPath;       // 处理路径：local/redis

    // 便捷构造方法
    public static QuotaConsumedEvent create(String requestId,
                                            String tenantId,
                                            String resourceKey,
                                            Long tokensRequested,
                                            Boolean allowed,
                                            String reason,
                                            String policyVersion,
                                            Long remainingTokens,
                                            String traceId) {
        return QuotaConsumedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .requestId(requestId)
                .tenantId(tenantId)
                .resourceKey(resourceKey)
                .tokensRequested(tokensRequested)
                .tokensConsumed(allowed ? tokensRequested : 0L)
                .allowed(allowed)
                .reason(reason)
                .policyVersion(policyVersion)
                .remainingTokens(remainingTokens)
                .timestamp(Instant.now().toEpochMilli())
                .traceId(traceId)
                .build();
    }
}