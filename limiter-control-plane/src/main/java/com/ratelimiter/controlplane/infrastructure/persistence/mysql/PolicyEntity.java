package com.ratelimiter.controlplane.infrastructure.persistence.mysql;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 策略实体类
 * 映射到 policy 表
 */
@Data
@TableName("policy")
public class PolicyEntity {

    /**
     * 主键（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户 ID
     */
    @TableField("tenant_id")
    private String tenantId;

    /**
     * 资源标识
     */
    @TableField("resource_key")
    private String resourceKey;

    /**
     * 策略类型：TOKEN_BUCKET/FIXED_WINDOW/SLIDING_WINDOW
     */
    @TableField("policy_type")
    private String policyType;

    /**
     * 时间窗口（秒）
     */
    @TableField("window_seconds")
    private Integer windowSeconds;

    /**
     * 容量
     */
    private Long capacity;

    /**
     * 补充速率（tokens/sec）
     */
    @TableField("refill_rate")
    private BigDecimal refillRate;

    /**
     * 突发容量（可选）
     */
    @TableField("burst_capacity")
    private Long burstCapacity;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 版本号
     */
    private String version;

    /**
     * 元数据（JSON）
     */
    private String metadata;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间（自动填充）
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}