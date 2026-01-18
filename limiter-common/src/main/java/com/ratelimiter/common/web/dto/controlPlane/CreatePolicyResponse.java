package com.ratelimiter.common.web.dto.controlPlane;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CreatePolicyResponse {
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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
