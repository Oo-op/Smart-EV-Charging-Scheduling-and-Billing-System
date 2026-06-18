package com.example.charging.controller;

import com.example.charging.dto.DispatchRequest;
import com.example.charging.dto.DispatchResult;
import com.example.charging.dto.QueueStatusDTO;
import com.example.charging.enums.ChargeMode;
import com.example.charging.service.SchedulerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/scheduler")
public class SchedulerController {

    private final SchedulerService schedulerService;

    public SchedulerController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @PostMapping("/dispatch")
    public DispatchResult dispatch(@RequestBody DispatchRequest request) {
        return schedulerService.dispatch(request.getMode());
    }

    @GetMapping("/queue/{mode}")
    public QueueStatusDTO queue(@PathVariable ChargeMode mode) {
        return schedulerService.getQueue(mode);
    }
}
