package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.ChargingRequestStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DispatchResult {

    private String algorithm;
    private Long requestId;
    private Long pileId;
    private String pileCode;
    private ChargeMode mode;
    private BigDecimal remainingAmount;
    private Long estimatedDurationMinutes;
    private ChargingRequestStatus requestStatus;
    private ChargingPileStatus pileStatus;
    private LocalDateTime estimatedStartTime;
    private String temporarySimulationNote;
}
