package com.example.charging.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class VehicleBindRequest {
    private Long userId;
    private String plateNumber;
    private String model;
    private BigDecimal batteryCapacity;
}