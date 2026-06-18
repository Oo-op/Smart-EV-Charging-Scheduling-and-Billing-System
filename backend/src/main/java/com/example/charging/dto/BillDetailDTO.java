package com.example.charging.dto;

import com.example.charging.enums.BillStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BillDetailDTO {
    private Long billId;
    private Long userId;
    private Long sessionId;
    private String pileCode;
    private String mode;
    private BigDecimal chargedAmount;
    private BigDecimal electricityFee;
    private BigDecimal serviceFee;
    private BigDecimal totalFee;
    private BillStatus status;
    private LocalDateTime createdAt;
    private String breakdown; // Peak/Flat/Valley time-of-use calculation details
}
