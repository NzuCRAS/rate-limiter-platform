package com.ratelimiter.dataplane.domain;

import com.ratelimiter.common.web.dto. dataPlane.PolicyDto;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java. util.Map;
import java. util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PolicyCache {

    private final Map<String, PolicyDto> policies = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public PolicyCache(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // 注册缓存大小指标
        meterRegistry.gauge("rate_limit_policy_cache_size", policies, Map:: size);
    }

    public void updatePolicies(List<PolicyDto> newPolicies) {
        log.info("Updating policy cache with {} policies", newPolicies. size());

        policies.clear();

        for (PolicyDto policy : newPolicies) {
            if (policy.getEnabled() != null && policy.getEnabled()) {
                String key = policy.getCacheKey();
                policies.put(key, policy);
                log.debug("Cached policy: tenant={}, resource={}, capacity={}",
                        policy.getTenantId(), policy.getResourceKey(), policy.getCapacity());
            }
        }

        log.info("Policy cache updated successfully, active policies: {}", policies.size());
    }

    /**
     * 查找策略
     */
    public PolicyDto findPolicy(String tenantId, String resourceKey) {
        String key = tenantId + "|" + resourceKey;
        return policies.get(key);
    }

    /**
     * 获取缓存状态
     */
    public int getCachedPolicyCount() {
        return policies.size();
    }
}