package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingSessionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SessionDTO {

    private Long sessionId;
    private Long requestId;
    private Long pileId;
    private ChargeMode mode;
    private BigDecimal targetAmount;
    private BigDecimal chargedAmount;
    private ChargingSessionStatus status;
    private LocalDateTime startTime;
    private LocalDateTime estimatedEndTime;
}
