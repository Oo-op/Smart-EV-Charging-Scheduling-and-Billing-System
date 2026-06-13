package com.example.charging.enums;

import lombok.Getter;

/**
 * 支付单状态（统一按 api-spec.md 1.3）
 * - PENDING：等待支付结果
 * - SUCCESS：支付成功
 * - FAILED：支付失败
 */
@Getter
public enum PaymentStatus {

    PENDING("PENDING", "待支付"),
    SUCCESS("SUCCESS", "支付成功"),
    FAILED("FAILED", "支付失败");

    private final String code;
    private final String desc;

    PaymentStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
