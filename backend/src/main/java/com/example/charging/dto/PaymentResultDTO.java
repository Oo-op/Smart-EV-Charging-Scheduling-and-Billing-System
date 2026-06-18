package com.example.charging.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentResultDTO {
    private Long billId;
    private Long paymentId;
    private String paymentMethod;
    private BigDecimal amount;
    private String billStatus;    // "PAID"
    private String paymentStatus; // "SUCCESS"
    private LocalDateTime paidAt;
}
