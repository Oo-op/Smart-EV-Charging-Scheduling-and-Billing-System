package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingRequestStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChargingRequestDetailDTO {
    private Long requestId;
    private Long userId;
    private Long vehicleId;
    private ChargeMode mode;
    private BigDecimal targetAmount;
    private BigDecimal chargedAmount;
    private ChargingRequestStatus status;
    private Integer queueNumber;
    private Long assignedPileId;
    private LocalDateTime createdAt;
}