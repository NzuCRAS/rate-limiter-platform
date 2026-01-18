package com.ratelimiter.dataplane.domain;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryPolicyStore {

    public static class SimplePolicy {
        public final long capacity;
        public final double refillRate;
        public final String version;

        public SimplePolicy(long capacity, double refillRate, String version) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.version = version;
        }
    }

    // key: tenantId + "|" + resourceKey
    private final Map<String, SimplePolicy> policies = new ConcurrentHashMap<>();

    public InMemoryPolicyStore() {
        // TODO: 这里只是示例，你可以在构造里临时塞一个默认策略
        // 比如针对所有请求统一一个策略，或针对特定 tenant+resource
        policies.put("tenant_001|/api/v1/orders",
                new SimplePolicy(1000L, 10.0, "v1"));
    }

    public SimplePolicy findPolicy(String tenantId, String resourceKey) {
        return policies.get(tenantId + "|" + resourceKey);
    }
}