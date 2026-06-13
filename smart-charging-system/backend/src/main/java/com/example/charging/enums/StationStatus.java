package com.example.charging.enums;

import lombok.Getter;

/**
 * 充电站（站点）状态
 */
@Getter
public enum StationStatus {

    AVAILABLE("AVAILABLE", "空闲可用"),
    OCCUPIED("OCCUPIED", "使用中"),
    MAINTENANCE("MAINTENANCE", "维护中"),
    OFFLINE("OFFLINE", "离线");

    private final String code;
    private final String desc;

    StationStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
