package com.ratelimiter.common.web.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ErrorInfo {

    private String code;

    private String message;

    private Map<String,Object> details;
}
