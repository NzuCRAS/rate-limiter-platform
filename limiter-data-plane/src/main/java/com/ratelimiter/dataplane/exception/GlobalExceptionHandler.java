package com.ratelimiter.dataplane.exception;

import com.ratelimiter.common.web.constant.ErrorCode;
import com.ratelimiter.common.web.dto.ApiResponse;
import com.ratelimiter.common.web.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        Map<String, Object> details = new HashMap<>(ex.getDetails());
        details.put("path", request.getRequestURI());

        return ApiResponse.fail(
                code.getCode(),
                ex.getMessage(),
                details
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // 从 ex 中收集字段错误信息，构造成 details
        // 使用 ErrorCode.INVALID_ARGUMENT
        ErrorCode code = ErrorCode.INVALID_ARGUMENT;
        Map<String, Object> details = new HashMap<>();
        details.put("details", ex.getBody().getDetail());
        details.put("path", request.getRequestURI());

        return ApiResponse.fail(
                code.getCode(),
                ex.getMessage(),
                details
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex, HttpServletRequest request) {
        // 记录日志
        // 返回 INTERNAL_ERROR
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        Map<String, Object> details = new HashMap<>();
        details.put("exMessage", ex.getMessage());
        details.put("path", request.getRequestURI());

        return ApiResponse.fail(
                code.getCode(),
                ex.getMessage(),
                details
        );
    }
}
