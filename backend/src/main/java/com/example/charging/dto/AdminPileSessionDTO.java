package com.example.charging.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminPileSessionDTO {

    private Long sessionId;
    private Long requestId;
    private BigDecimal chargedAmount;
    private BigDecimal targetAmount;
    private LocalDateTime startTime;
}
