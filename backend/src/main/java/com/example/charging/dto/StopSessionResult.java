package com.example.charging.dto;

import com.example.charging.enums.ChargingSessionStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StopSessionResult {

    private Long sessionId;
    private Long requestId;
    private Long pileId;
    private BigDecimal chargedAmount;
    private ChargingSessionStatus status;
    private LocalDateTime endTime;
    private BillSummaryDTO bill;
}
