package com.ratelimiter.accounting.infrastructure.persistence.mysql;

import com.baomidou. mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quota_audit")
public class QuotaAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("request_id")
    private String requestId;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("resource_key")
    private String resourceKey;

    private Long tokens;              // 对应数据库的 tokens 字段

    private Boolean allowed;          // 对应数据库的 allowed 字段

    private Long remaining;           // 对应数据库的 remaining 字段

    private String reason;            // 对应数据库的 reason 字段

    @TableField("policy_version")
    private String policyVersion;

    @TableField("client_ip")
    private String clientIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("latency_ms")
    private Integer latencyMs;

    private Long timestamp;           // 对应数据库的 timestamp 字段

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // 为了兼容事件数据，添加一些辅助字段（不映射到数据库）
    @TableField(exist = false)
    private String eventId;           // 事件ID，用于幂等判断

    @TableField(exist = false)
    private String traceId;           // 链路追踪ID，可记录到 client_ip 或其他字段
}