package com.example.charging.dto;

import lombok.Data;

@Data
public class PileSummaryDTO {

    private long idle;
    private long charging;
    private long fault;
    private long offline;
}
