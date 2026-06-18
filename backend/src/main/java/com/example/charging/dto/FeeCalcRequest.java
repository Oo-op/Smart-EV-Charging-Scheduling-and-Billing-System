package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.PricePeriod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计费输入：一次充电区间与充电总量
 */
@Data
@Builder
public class FeeCalcRequest {

    private ChargeMode mode;
    private BigDecimal energyKwh;         // 总充电量（度）
    private LocalDateTime startTime;      // 充电开始时间
    private LocalDateTime endTime;        // 充电结束时间

    /** 预估模式：给定总电量 & 充电时长，按时段加权分配电量 */
    public static class Segment {
        public PricePeriod period;
        public BigDecimal energyKwh;

        public Segment(PricePeriod period, BigDecimal energyKwh) {
            this.period = period;
            this.energyKwh = energyKwh;
        }
    }
}
