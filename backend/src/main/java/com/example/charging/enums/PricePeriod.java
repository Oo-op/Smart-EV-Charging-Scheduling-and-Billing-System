package com.example.charging.enums;

import lombok.Getter;

/**
 * 电价时段：峰 / 平 / 谷
 */
@Getter
public enum PricePeriod {

    PEAK("PEAK", "高峰"),
    FLAT("FLAT", "平峰"),
    VALLEY("VALLEY", "低谷");

    private final String code;
    private final String desc;

    PricePeriod(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 默认按小时归属判断（可被业务层按实际规则覆盖）：
     * 高峰：08-11 & 18-21；平：12-17；谷：0-07 & 22-23。
     */
    public static PricePeriod ofHour(int hour) {
        if ((hour >= 8 && hour <= 11) || (hour >= 18 && hour <= 21)) {
            return PEAK;
        }
        if (hour >= 12 && hour <= 17) {
            return FLAT;
        }
        return VALLEY;
    }
}
