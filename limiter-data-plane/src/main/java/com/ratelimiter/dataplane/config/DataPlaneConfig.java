package com.ratelimiter.dataplane.config;

import org. springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableScheduling  // 启用定时任务
public class DataPlaneConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}