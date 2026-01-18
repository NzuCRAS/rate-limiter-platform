package com.ratelimiter.common.web.constant;

public enum ErrorCode {

    // 通用
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error"),
    INVALID_ARGUMENT("INVALID_ARGUMENT", "Invalid request parameter"),

    // 租户/策略相关
    TENANT_NOT_FOUND("TENANT_NOT_FOUND", "Tenant not found"),
    POLICY_NOT_FOUND("POLICY_NOT_FOUND", "Policy not found"),
    POLICY_ALREADY_EXISTS("POLICY_ALREADY_EXISTS", "Policy already exists"),
    POLICY_PERSISTENCE_ERROR("POLICY_PERSISTENCE_ERROR", "Policy persistence error"),

    // 限流/配额相关
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "Quota exceeded"),
    POLICY_DISABLED("POLICY_DISABLED", "Policy is disabled"),

    // 审计/幂等相关
    ORIGINAL_REQUEST_NOT_FOUND("ORIGINAL_REQUEST_NOT_FOUND", "Original request not found"),
    DUPLICATE_REQUEST("DUPLICATE_REQUEST", "Duplicate request");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String internalError, String s) {
        this.code = internalError;
        this.defaultMessage = s;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

}