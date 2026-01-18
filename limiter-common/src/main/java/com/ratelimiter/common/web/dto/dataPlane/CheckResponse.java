package com.ratelimiter.common.web.dto.dataPlane;

import lombok.Data;

@Data
public class CheckResponse {
    private boolean allowed;

    private Long remaining;        // 剩余配额（不一定精确，视实现）

    private String policyVersion;

    private String reason;         // "", "quota_exceeded", "policy_disabled"...

    private String tenantId;

    private String resourceKey;

    private String requestId;      // 回显，便于客户端对账

    private Long timestamp;        // 服务端处理时间戳（可选）
}
