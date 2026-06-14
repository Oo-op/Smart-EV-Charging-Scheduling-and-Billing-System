package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ChargingRequestSubmitRequest {
    private Long userId;
    private Long vehicleId;
    private ChargeMode mode;
    private BigDecimal targetAmount;
}