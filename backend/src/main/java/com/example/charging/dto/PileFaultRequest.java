package com.example.charging.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PileFaultRequest {

    private String faultReason;
    private BigDecimal chargedAmount;
}
