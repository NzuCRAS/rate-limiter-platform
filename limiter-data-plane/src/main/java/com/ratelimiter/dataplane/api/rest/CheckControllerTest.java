package com.ratelimiter.dataplane.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class CheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void check() throws Exception {
        String json = """
            {
              "requestId": "req-123",
              "tenantId": "tenant_001",
              "resourceKey": "/api/v1/orders",
              "tokens": 1,
              "timestamp": 1700000000000
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/v1/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value("req-123"))
                .andExpect(jsonPath("$.requestId").value("req-123"))
                .andReturn();

        Object requestJsonObj =  objectMapper.readValue(json, Object.class);
        String requestLog =  objectMapper.writerWithDefaultPrettyPrinter().
                writeValueAsString(requestJsonObj);
        System.out.println("request body:\n" + requestLog);

        String responseBody = result.getResponse().getContentAsString();
        Object responseJsonObj = objectMapper.readValue(responseBody, Object.class);
        String responseLog = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(responseJsonObj);
        System.out.println("response body:\n" + responseLog);
    }
}
