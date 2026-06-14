package com.example.charging.dto;

import lombok.Data;

@Data
public class AdminQueueDTO {

    private AdminQueueModeDTO fastQueue;
    private AdminQueueModeDTO slowQueue;
}
