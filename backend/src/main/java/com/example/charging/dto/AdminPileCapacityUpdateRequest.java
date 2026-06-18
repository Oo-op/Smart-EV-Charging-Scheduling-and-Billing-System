package com.example.charging.dto;

import lombok.Data;

@Data
public class AdminPileCapacityUpdateRequest {
    private Boolean enabled;
    private Integer openQueueSlots;
}
