package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import lombok.Data;

import java.util.List;

@Data
public class AdminQueueModeDTO {

    private ChargeMode mode;
    private int queueLength;
    private List<AdminQueueItemDTO> waitingList;
}
