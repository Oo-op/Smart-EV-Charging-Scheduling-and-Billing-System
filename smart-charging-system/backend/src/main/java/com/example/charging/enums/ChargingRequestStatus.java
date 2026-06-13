package com.example.charging.enums;

import lombok.Getter;

/**
 * 充电请求状态（统一按 api-spec.md 1.3）
 * - WAITING：已提交，等待调度
 * - ASSIGNED：已分配到充电桩
 * - CHARGING：充电中
 * - CANCELLED：已取消
 * - COMPLETED：充电完成
 */
@Getter
public enum ChargingRequestStatus {

    WAITING("WAITING", "等待调度"),
    ASSIGNED("ASSIGNED", "已分配充电桩"),
    CHARGING("CHARGING", "充电中"),
    CANCELLED("CANCELLED", "已取消"),
    COMPLETED("COMPLETED", "已完成");

    private final String code;
    private final String desc;

    ChargingRequestStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
