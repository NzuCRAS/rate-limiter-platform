package com.ratelimiter.controlplane.api;

import com.ratelimiter.common.web.dto.ApiResponse;
import com.ratelimiter.common.web.dto.controlPlane.CreatePolicyRequest;
import com.ratelimiter.common.web.dto.controlPlane.CreatePolicyResponse;
import com.ratelimiter.common.web.dto.controlPlane.GetPolicyResponse;
import com.ratelimiter.controlplane.application.PolicyService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    public ApiResponse<CreatePolicyResponse> createPolicy(@RequestBody CreatePolicyRequest request) {
        CreatePolicyResponse created = policyService.createPolicy(request);
        // 如果 service 内部抛 BusinessException，交给 GlobalExceptionHandler 统一处理
        return ApiResponse.ok(created);
    }

    @GetMapping("/{id}")
    public ApiResponse<GetPolicyResponse> getPolicy(@PathVariable Long id) {
        GetPolicyResponse getPolicyResponse = policyService.getPolicyById(id);
        // service 中若找不到，抛 BusinessException(ErrorCode.POLICY_NOT_FOUND)
        return ApiResponse.ok(getPolicyResponse);
    }
}