package com.example.charging.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 计费输出：电费、服务费、总金额
 */
@Data
@Builder
public class FeeCalcResult {

    private BigDecimal electricityFee;   // 电费
    private BigDecimal serviceFee;       // 服务费
    private BigDecimal totalFee;         // 合计
    private BigDecimal energyKwh;        // 充电总量
    private String breakdown;            // 明细描述，便于前端展示
}
