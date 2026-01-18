package com.ratelimiter.common.web.dto.controlPlane;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePolicyRequest {

    private String tenantId;

    private String resourceKey;

    private String policyType;

    private Integer windowSeconds;

    private Long capacity;

    private BigDecimal refillRate;

    private Long burstCapacity;

    private Integer priority = 0;

    private Boolean enabled = true;

    private String version = "v1";

    private String metadata;

    private String description;
}
