package com.example.charging.enums;

import lombok.Getter;

/**
 * 充电会话状态（统一按 api-spec.md 1.3）
 * - CHARGING：充电中
 * - COMPLETED：正常结束
 * - INTERRUPTED：被故障或外部停止打断
 */
@Getter
public enum ChargingSessionStatus {

    CHARGING("CHARGING", "充电中"),
    COMPLETED("COMPLETED", "已完成"),
    INTERRUPTED("INTERRUPTED", "被中断");

    private final String code;
    private final String desc;

    ChargingSessionStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
