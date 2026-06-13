package com.example.charging.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class VehicleDTO {
    private Long vehicleId;
    private Long userId;
    private String plateNumber;
    private String model;
    private BigDecimal batteryCapacity;
}