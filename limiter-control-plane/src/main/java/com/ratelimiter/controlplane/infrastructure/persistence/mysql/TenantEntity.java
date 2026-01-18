package com.ratelimiter.controlplane.infrastructure.persistence.mysql;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户信息类
 * 映射到tenant表
 */
@Data
@TableName("tenant")
public class TenantEntity {
    @TableId(type = IdType. AUTO)
    private Long id;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("tenant_name")
    private String tenantName;

    @TableField("contact_email")
    private String contactEmail;

    private String tier;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}