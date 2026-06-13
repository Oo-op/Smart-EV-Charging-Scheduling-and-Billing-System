package com.example.charging.dto;

import lombok.Data;

@Data
public class StartSessionRequest {

    private Long requestId;
    private Long pileId;
}
