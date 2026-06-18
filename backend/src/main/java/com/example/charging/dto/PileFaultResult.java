package com.example.charging.dto;

import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.enums.ChargingSessionStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PileFaultResult {

    private Long pileId;
    private ChargingPileStatus status;
    private String faultReason;
    private Long interruptedSessionId;
    private ChargingSessionStatus sessionStatus;
    private Long recoveredRequestId;
    private ChargingRequestStatus requestStatus;
    private BigDecimal chargedAmount;
    private BigDecimal remainingAmount;
    private Boolean temporarySimulation;
    private String temporarySimulationNote;
}
