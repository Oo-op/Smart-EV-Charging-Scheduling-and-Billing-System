package com.example.charging.controller;

import com.example.charging.dto.PileDTO;
import com.example.charging.dto.AdminPileCapacityUpdateRequest;
import com.example.charging.dto.PileFaultRequest;
import com.example.charging.dto.PileFaultResult;
import com.example.charging.dto.PileRecoverResult;
import com.example.charging.service.PileService;
import com.example.charging.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/piles")
public class PileController {

    private final PileService pileService;
    private final UserService userService;

    public PileController(PileService pileService, UserService userService) {
        this.pileService = pileService;
        this.userService = userService;
    }

    @GetMapping
    public List<PileDTO> list() {
        return pileService.listPiles();
    }

    @PatchMapping("/{pileId}/capacity")
    public PileDTO updateCapacity(@PathVariable Long pileId,
                                  @RequestHeader(value = "Authorization", required = false) String authorization,
                                  @RequestBody AdminPileCapacityUpdateRequest request) {
        userService.requireAdmin(authorization);
        return pileService.updateCapacity(pileId, request);
    }

    @PostMapping("/{pileId}/fault")
    public PileFaultResult markFault(@PathVariable Long pileId,
                                     @RequestHeader(value = "Authorization", required = false) String authorization,
                                     @RequestBody(required = false) PileFaultRequest request) {
        userService.requireAdmin(authorization);
        String reason = request == null ? null : request.getFaultReason();
        return pileService.markFault(pileId, reason, request == null ? null : request.getChargedAmount());
    }

    @PostMapping("/{pileId}/recover")
    public PileRecoverResult recover(@PathVariable Long pileId,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return pileService.recover(pileId);
    }
}
