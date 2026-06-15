package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminPileDTO {

    private Long pileId;
    private String code;
    private ChargeMode mode;
    private BigDecimal power;
    private ChargingPileStatus status;
    private AdminPileSessionDTO currentSession;
}
