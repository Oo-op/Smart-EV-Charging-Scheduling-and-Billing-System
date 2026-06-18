package com.example.charging.enums;

import lombok.Getter;

/**
 * 充电模式：充电策略选择
 */
@Getter
public enum ChargeMode {

    FAST("FAST", "快充"),
    NORMAL("NORMAL", "普通充电"),
    SLOW("SLOW", "慢充"),
    SMART("SMART", "智能充电（定时/谷电）"),
    SCHEDULED("SCHEDULED", "定时充电");

    private final String code;
    private final String desc;

    ChargeMode(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
