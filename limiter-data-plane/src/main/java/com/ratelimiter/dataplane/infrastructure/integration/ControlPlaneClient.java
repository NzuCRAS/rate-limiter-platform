package com.ratelimiter.dataplane.infrastructure.integration;

import com. ratelimiter. common.web.dto.ApiResponse;
import com.ratelimiter.common.web. dto.controlPlane.GetPolicyResponse;
import com.ratelimiter.common. web.dto.dataPlane.PolicyDto;
import lombok.extern.slf4j. Slf4j;
import org.springframework.beans.factory.annotation. Value;
import org. springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework. http.ResponseEntity;
import org.springframework.stereotype. Component;
import org. springframework.web.client.RestTemplate;

import java.util. Collections;
import java. util.List;
import java.util. stream.Collectors;

@Slf4j
@Component
public class ControlPlaneClient {

    private final RestTemplate restTemplate;
    private final String controlPlaneBaseUrl;

    public ControlPlaneClient(RestTemplate restTemplate,
                              @Value("${app.control-plane.base-url: http://localhost:8081}") String controlPlaneBaseUrl) {
        this.restTemplate = restTemplate;
        this.controlPlaneBaseUrl = controlPlaneBaseUrl;
    }

    /**
     * 从 Control Plane 拉取所有启用的策略
     */
    public List<PolicyDto> fetchAllEnabledPolicies() {
        try {
            String url = controlPlaneBaseUrl + "/api/v1/policies/enabled";

            ResponseEntity<ApiResponse<List<GetPolicyResponse>>> response = restTemplate. exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse<List<GetPolicyResponse>>>() {}
            );

            if (response.getBody() != null && response.getBody().isSuccess()) {
                List<GetPolicyResponse> policies = response. getBody().getData();
                return policies. stream()
                        .map(this:: toPolicyDto)
                        .collect(Collectors.toList());
            } else {
                log.warn("Failed to fetch policies: {}", response.getBody());
                return Collections. emptyList();
            }
        } catch (Exception e) {
            log. error("Error fetching policies from Control Plane", e);
            return Collections.emptyList();
        }
    }

    /**
     * GetPolicyResponse -> PolicyDto 转换
     */
    private PolicyDto toPolicyDto(GetPolicyResponse response) {
        PolicyDto dto = new PolicyDto();
        dto.setId(response.getId());
        dto.setTenantId(response. getTenantId());
        dto.setResourceKey(response. getResourceKey());
        dto.setPolicyType(response.getPolicyType());
        dto.setWindowSeconds(response.getWindowSeconds());
        dto.setCapacity(response.getCapacity());
        dto.setRefillRate(response.getRefillRate());
        dto.setBurstCapacity(response.getBurstCapacity());
        dto.setPriority(response. getPriority());
        dto.setEnabled(response.getEnabled());
        dto.setVersion(response.getVersion());
        dto.setMetadata(response.getMetadata());
        dto.setDescription(response.getDescription());
        return dto;
    }
}