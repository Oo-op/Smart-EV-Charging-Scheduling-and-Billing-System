package com.example.charging.dto;

import com.example.charging.enums.ChargingPileStatus;
import lombok.Data;

@Data
public class PileRecoverResult {

    private Long pileId;
    private ChargingPileStatus status;
    private DispatchResult dispatchResult;
    private Boolean temporarySimulation;
    private String temporarySimulationNote;
}
