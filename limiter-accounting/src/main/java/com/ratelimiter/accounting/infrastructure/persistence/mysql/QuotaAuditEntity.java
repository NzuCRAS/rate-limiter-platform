package com.ratelimiter.accounting.infrastructure.persistence.mysql;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quota_audit")
public class QuotaAuditEntity {
    @TableId(type = IdType. AUTO)
    private Long id;

    @TableField("request_id")
    private String requestId;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("resource_key")
    private String resourceKey;

    private Long tokens;

    private Boolean allowed;

    private Long remaining;

    private String reason;

    @TableField("policy_version")
    private String policyVersion;

    @TableField("client_ip")
    private String clientIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("latency_ms")
    private Integer latencyMs;

    private Long timestamp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
