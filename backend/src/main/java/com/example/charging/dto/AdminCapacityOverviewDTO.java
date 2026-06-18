package com.example.charging.dto;

import lombok.Data;

@Data
public class AdminCapacityOverviewDTO {
    private long totalPiles;
    private long enabledPiles;
    private long fastEnabledPiles;
    private long slowEnabledPiles;
    private int totalOpenQueueSlots;
    private int totalMaxQueueSlots;
}
