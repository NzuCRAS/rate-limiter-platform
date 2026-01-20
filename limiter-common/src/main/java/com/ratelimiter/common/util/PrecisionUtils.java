package com.ratelimiter.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PrecisionUtils {

    /**
     * BigDecimal -> double（用于内部计算）
     * 保留合理的精度，避免精度丢失过多
     */
    public static double toDouble(BigDecimal decimal) {
        return decimal == null ? 0.0 : decimal. doubleValue();
    }

    /**
     * double -> BigDecimal（用于存储和 API 返回）
     * 保留 4 位小数，适合 refillRate 场景
     */
    public static BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 安全的 BigDecimal 运算（避免 null）
     */
    public static BigDecimal multiply(BigDecimal a, double b) {
        if (a == null) return BigDecimal.ZERO;
        return a.multiply(BigDecimal.valueOf(b));
    }

    /**
     * 限流场景的 token 计算：时间差 * 速率
     */
    public static double calculateTokenDelta(BigDecimal refillRate, double timeElapsedSeconds) {
        if (refillRate == null || timeElapsedSeconds <= 0) {
            return 0.0;
        }
        return refillRate.doubleValue() * timeElapsedSeconds;
    }
}