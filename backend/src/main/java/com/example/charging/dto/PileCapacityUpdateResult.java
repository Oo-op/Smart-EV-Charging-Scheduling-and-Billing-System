package com.example.charging.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PileCapacityUpdateResult {

    private PileDTO pile;
    /** 是否因开放桩位触发了同模式调度 */
    private Boolean dispatchTriggered;
    /** 本次调度新分配的请求数 */
    private Integer assignedCount;
    private List<DispatchResult> dispatchResults = new ArrayList<>();
    private String note;
}
