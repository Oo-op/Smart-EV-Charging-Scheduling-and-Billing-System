package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import lombok.Data;

import java.util.List;

@Data
public class AdminQueueModeDTO {

    private ChargeMode mode;
    private int queueLength;
    private int availablePileCount;
    private int totalOpenQueueSlots;
    private int remainingQueueCapacity;
    private int estimatedWaitTime;
    private String temporarySimulationNote;
    private List<AdminQueueItemDTO> waitingList;
}
