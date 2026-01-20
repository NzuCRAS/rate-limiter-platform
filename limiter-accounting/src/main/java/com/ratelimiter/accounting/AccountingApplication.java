package com.ratelimiter.accounting;

import org. mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org. springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.ratelimiter.accounting.infrastructure.persistence.mapper")
@ComponentScan({
        "com.ratelimiter.accounting",
        "com.ratelimiter.common.web"  // 扫描公共web组件
})
public class AccountingApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccountingApplication.class, args);
    }
}