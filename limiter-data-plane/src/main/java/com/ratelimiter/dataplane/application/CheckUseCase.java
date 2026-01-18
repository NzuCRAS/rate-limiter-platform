package com.ratelimiter.dataplane.application;

import com.ratelimiter.common.web.dto.dataPlane.CheckRequest;
import com.ratelimiter.common.web.dto.dataPlane.CheckResponse;

public interface CheckUseCase {

    CheckResponse checkAndConsume(CheckRequest request);
}
