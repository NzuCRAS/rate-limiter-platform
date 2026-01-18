package com.ratelimiter.dataplane.api.rest;


import com.ratelimiter.common.web.dto.ApiResponse;
import com.ratelimiter.common.web.dto.dataPlane.CheckRequest;
import com.ratelimiter.common.web.dto.dataPlane.CheckResponse;
import com.ratelimiter.dataplane.application.CheckUseCase;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CheckController {

    private final CheckUseCase checkUseCase;

    @PostMapping("/check")
    public ApiResponse<CheckResponse> check(@RequestBody CheckRequest request) {
        CheckResponse result = checkUseCase.checkAndConsume(request);
        // 把业务 requestId 同步写到统一响应包装里
        return ApiResponse.ok(result, request.getRequestId());
    }
}
