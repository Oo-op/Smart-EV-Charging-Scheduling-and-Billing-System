package com.example.charging.controller;

import com.example.charging.dto.ChargingRequestDetailDTO;
import com.example.charging.dto.ChargingRequestModifyRequest;
import com.example.charging.dto.ChargingRequestSubmitRequest;
import com.example.charging.service.ChargingRequestService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/charging-requests")
public class ChargingRequestController {

    private final ChargingRequestService requestService;

    public ChargingRequestController(ChargingRequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    public ChargingRequestDetailDTO submit(@RequestBody ChargingRequestSubmitRequest request) {
        return requestService.submitRequest(request);
    }

    @GetMapping("/user/{userId}")
    public List<ChargingRequestDetailDTO> listByUser(@PathVariable Long userId) {
        return requestService.listByUserId(userId);
    }

    @GetMapping("/{requestId}")
    public ChargingRequestDetailDTO getDetail(@PathVariable Long requestId,
                                              @RequestParam Long userId) {
        return requestService.getRequestDetail(requestId, userId);
    }

    @PostMapping("/{requestId}/cancel")
    public ChargingRequestDetailDTO cancel(@PathVariable Long requestId,
                                           @RequestParam Long userId) {
        return requestService.cancelRequest(requestId, userId);
    }

    @PostMapping("/{requestId}/modify")
    public ChargingRequestDetailDTO modify(@PathVariable Long requestId,
                                           @RequestParam Long userId,
                                           @RequestBody ChargingRequestModifyRequest modifyRequest) {
        return requestService.modifyRequest(requestId, userId, modifyRequest);
    }
}