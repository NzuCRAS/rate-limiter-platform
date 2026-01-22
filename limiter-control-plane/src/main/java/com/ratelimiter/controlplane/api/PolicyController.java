package com.ratelimiter.controlplane.api;

import com. ratelimiter.common.web.dto.ApiResponse;
import com.ratelimiter.common.web.dto.controlPlane.CreatePolicyRequest;
import com. ratelimiter.common.web.dto.controlPlane.CreatePolicyResponse;
import com. ratelimiter.common.web.dto.controlPlane.GetPolicyResponse;
import com. ratelimiter.common.web.exception.BusinessException;
import com. ratelimiter.controlplane. application.PolicyService;
import com.ratelimiter.controlplane.application.metrics.PolicyMetricsService;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;
    private final PolicyMetricsService metricsService;

    /**
     * 创建策略
     */
    @PostMapping
    public ApiResponse<CreatePolicyResponse> createPolicy(@RequestBody CreatePolicyRequest request) {
        Timer.Sample sample = metricsService.startPolicyCreate(request.getTenantId());

        try {
            CreatePolicyResponse created = policyService.createPolicy(request);
            metricsService.finishPolicyCreate(sample, true, request.getTenantId(), null);
            return ApiResponse.ok(created);
        } catch (BusinessException ex) {
            String errorType = ex.getErrorCode().getCode();
            metricsService.finishPolicyCreate(sample, false, request. getTenantId(), errorType);

            // 记录具体错误类型
            if ("POLICY_ALREADY_EXISTS".equals(errorType)) {
                metricsService.recordPolicyDuplicate(request.getTenantId(), request.getResourceKey());
            } else if (errorType.contains("VALIDATION")) {
                metricsService. recordPolicyValidationFailed(request.getTenantId(), request.getResourceKey(), ex.getMessage());
            }

            throw ex;
        } catch (Exception ex) {
            metricsService.finishPolicyCreate(sample, false, request.getTenantId(), "internal_error");
            throw ex;
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<GetPolicyResponse> getPolicy(@PathVariable Long id) {
        long startTime = System.currentTimeMillis();

        try {
            GetPolicyResponse policy = policyService.getPolicyById(id);
            long duration = System.currentTimeMillis() - startTime;

            metricsService.recordPolicyQuery("get_by_id", policy.getTenantId(), true, duration);
            return ApiResponse.ok(policy);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordPolicyQuery("get_by_id", "unknown", false, duration);
            throw ex;
        }
    }

    /**
     * 查询指定租户的所有启用策略
     */
    @GetMapping
    public ApiResponse<List<GetPolicyResponse>> listPolicies(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "true") Boolean enabledOnly) {

        long startTime = System.currentTimeMillis();

        try {
            List<GetPolicyResponse> policies = policyService.listPolicies(tenantId, enabledOnly);
            long duration = System.currentTimeMillis() - startTime;

            String operation = tenantId != null ? "list_by_tenant" : "list_all";
            metricsService.recordPolicyQuery(operation, tenantId != null ? tenantId : "all", true, duration);

            return ApiResponse.ok(policies);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordPolicyQuery("list_policies", tenantId != null ? tenantId : "all", false, duration);
            throw ex;
        }
    }

    /**
     * 查询所有启用策略（供 Data Plane 批量拉取）
     */
    @GetMapping("/enabled")
    public ApiResponse<List<GetPolicyResponse>> listAllEnabledPolicies() {
        long startTime = System. currentTimeMillis();

        try {
            List<GetPolicyResponse> policies = policyService.listAllEnabledPolicies();
            long duration = System.currentTimeMillis() - startTime;

            metricsService.recordPolicyQuery("list_enabled", "all", true, duration);
            return ApiResponse.ok(policies);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordPolicyQuery("list_enabled", "all", false, duration);
            throw ex;
        }
    }
}