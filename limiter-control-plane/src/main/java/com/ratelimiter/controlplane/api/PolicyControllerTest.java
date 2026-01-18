package com.ratelimiter.controlplane.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.common.web.dto.controlPlane.CreatePolicyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private CreatePolicyRequest buildCreateRequest(String tenantId, String resourceKey) {
        CreatePolicyRequest req = new CreatePolicyRequest();
        req.setTenantId(tenantId);
        req.setResourceKey(resourceKey);
        req.setPolicyType("TOKEN_BUCKET");
        req.setWindowSeconds(60);
        req.setCapacity(1000L);
        req.setRefillRate(new BigDecimal("16.67"));
        req.setBurstCapacity(2000L);
        req.setPriority(10);
        req.setEnabled(true);
        req.setVersion("v1");
        req.setMetadata("{\"note\":\"test\"}");
        req.setDescription("test policy");
        return req;
    }

    @Test
    void createPolicy_shouldReturnCreatedPolicy() throws Exception {
        CreatePolicyRequest req = buildCreateRequest("tenant_001", "/api/v1/orders");

        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.tenantId").value("tenant_001"))
                .andExpect(jsonPath("$.data.resourceKey").value("/api/v1/orders"));
    }

    @Test
    void createPolicy_duplicate_shouldReturnError() throws Exception {
        CreatePolicyRequest req = buildCreateRequest("tenant_001", "/api/v1/orders");

        // 第一次成功
        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk());

        // 第二次应失败（BusinessException -> GlobalExceptionHandler -> ApiResponse.fail）
        mockMvc.perform(post("/api/v1/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POLICY_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.details.tenantId").value("tenant_001"));
    }
}