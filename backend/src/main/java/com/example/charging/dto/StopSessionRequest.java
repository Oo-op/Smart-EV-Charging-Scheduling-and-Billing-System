package com.example.charging.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StopSessionRequest {

    private BigDecimal chargedAmount;
}
