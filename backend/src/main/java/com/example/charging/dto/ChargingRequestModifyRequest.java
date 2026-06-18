package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ChargingRequestModifyRequest {
    private ChargeMode mode;          // 可选，第一版可只允许修改 targetAmount
    private BigDecimal targetAmount;
}