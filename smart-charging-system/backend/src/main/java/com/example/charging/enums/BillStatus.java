package com.example.charging.enums;

import lombok.Getter;

/**
 * 账单状态（统一按 api-spec.md 1.3）
 * - UNPAID：待支付
 * - PAID：已支付
 * - CANCELLED：已取消（超时或主动取消）
 */
@Getter
public enum BillStatus {

    UNPAID("UNPAID", "待支付"),
    PAID("PAID", "已支付"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    BillStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
