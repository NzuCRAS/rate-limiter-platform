package com.ratelimiter.controlplane.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com. baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou. mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 */
@Configuration
public class MyBatisPlusConfig {

    /**
     * 分页插件（后续做分页查询时需要）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 添加分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 可以添加其他插件，如乐观锁、防全表更新等
        return interceptor;
    }
}