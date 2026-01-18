package com.ratelimiter.common.web.dto;

import lombok.Data;
import org.slf4j.MDC;

import java.util.Map;

@Data
public class ApiResponse<T> {

    private boolean success;

    private T data;

    private ErrorInfo error;

    private String traceId;

    private String requestId;

    public static <T> ApiResponse<T> ok(T data){
        return ApiResponse.ok(data,null);
    }

    public static <T> ApiResponse<T> ok(T data, String requestId){
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.setSuccess(true);
        apiResponse.setData(data);
        apiResponse.setRequestId(requestId);
        apiResponse.setTraceId(MDC.get("traceId"));

        // traceId从上下文中获取
        return apiResponse;
    }

    public static <T> ApiResponse<T> fail(ErrorInfo errorInfo){
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.setSuccess(false);
        apiResponse.setError(errorInfo);
        apiResponse.setTraceId(MDC.get("traceId"));

        // traceId从上下文中获取
        return apiResponse;
    }

    public static <T> ApiResponse<T> fail(String code, String message, Map<String,Object> details){
        ApiResponse<T> apiResponse = new ApiResponse<T>();
        apiResponse.setSuccess(false);
        ErrorInfo errorInfo = new ErrorInfo();
        errorInfo.setCode(code);
        errorInfo.setMessage(message);
        errorInfo.setDetails(details);
        apiResponse.setError(errorInfo);
        apiResponse.setTraceId(MDC.get("traceId"));
        return apiResponse;
    }
}
