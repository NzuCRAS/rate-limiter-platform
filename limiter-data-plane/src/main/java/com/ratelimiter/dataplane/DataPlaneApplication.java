package com.ratelimiter.dataplane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({
        "com.ratelimiter.dataplane",
        "com.ratelimiter.common.web"
})
public class DataPlaneApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataPlaneApplication.class, args);
    }
}
