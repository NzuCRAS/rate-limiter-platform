package com.ratelimiter.common.web.dto.dataPlane;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PolicyDto {
    private Long id;
    private String tenantId;
    private String resourceKey;
    private String policyType;
    private Integer windowSeconds;
    private Long capacity;
    private BigDecimal refillRate;
    private Long burstCapacity;
    private Integer priority;
    private Boolean enabled;
    private String version;
    private String metadata;
    private String description;

    // 生成缓存 key 的便捷方法
    public String getCacheKey() {
        return tenantId + "|" + resourceKey;
    }
}