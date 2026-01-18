package com.ratelimiter.common.web.dto.dataPlane;

import lombok.Data;

import java.util.Map;

@Data
public class CheckRequest {

    private String requestId;     // 业务幂等ID

    private String tenantId;

    private String resourceKey;

    private Long tokens;          // 本次消耗 token 数

    private Long timestamp;       // 请求发生时间（毫秒）

    private Map<String, Object> metadata;  // IP, userId 等扩展信息
}
