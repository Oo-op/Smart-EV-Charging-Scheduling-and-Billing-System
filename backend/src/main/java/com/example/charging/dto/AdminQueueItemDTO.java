package com.example.charging.dto;

import com.example.charging.enums.ChargingRequestStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminQueueItemDTO {

    private Long requestId;
    private String plateNumber;
    private BigDecimal targetAmount;
    private Integer queueNumber;
    private ChargingRequestStatus status;
}
