package com.example.charging.dto;

import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.enums.QueueArea;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class QueueItemDTO {

    private Long requestId;
    private Long vehicleId;
    private String plateNumber;
    private BigDecimal targetAmount;
    private BigDecimal chargedAmount;
    private BigDecimal remainingAmount;
    private Long estimatedDurationMinutes;
    private Integer queueNumber;
    private QueueArea queueArea;
    private Boolean recoveryPriority;
    private ChargingRequestStatus status;
    private LocalDateTime createdAt;
}
