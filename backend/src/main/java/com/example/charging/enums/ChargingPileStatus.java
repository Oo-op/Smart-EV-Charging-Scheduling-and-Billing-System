package com.example.charging.enums;

import lombok.Getter;

/**
 * 充电桩状态（统一按 api-spec.md 1.3）
 * - IDLE：空闲可用
 * - RESERVED：已被请求占用，等待车辆插枪
 * - CHARGING：充电中
 * - FAULT：故障
 * - OFFLINE：离线
 */
@Getter
public enum ChargingPileStatus {

    IDLE("IDLE", "空闲"),
    RESERVED("RESERVED", "已预约"),
    CHARGING("CHARGING", "充电中"),
    FAULT("FAULT", "故障"),
    OFFLINE("OFFLINE", "离线");

    private final String code;
    private final String desc;

    ChargingPileStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
