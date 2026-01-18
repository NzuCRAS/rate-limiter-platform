package com.ratelimiter.controlplane;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.ratelimiter.controlplane.infrastructure.persistence.mapper")
@ComponentScan({
        "com.ratelimiter.controlplane",
        "com.ratelimiter.common.web"   // 把 common-web 的包加进来
})
public class ControlPlaneApplication {
    public static void main(String[] args) {
        SpringApplication.run(ControlPlaneApplication.class, args);
    }
}