package com.example.charging.dto;

import com.example.charging.enums.BillStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BillSummaryDTO {

    private Long billId;
    private BigDecimal electricityFee;
    private BigDecimal serviceFee;
    private BigDecimal totalFee;
    private BillStatus status;
}
