package com.example.charging.controller;

import com.example.charging.dto.PileDTO;
import com.example.charging.dto.PileFaultRequest;
import com.example.charging.dto.PileFaultResult;
import com.example.charging.dto.PileRecoverResult;
import com.example.charging.service.PileService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/piles")
public class PileController {

    private final PileService pileService;

    public PileController(PileService pileService) {
        this.pileService = pileService;
    }

    @GetMapping
    public List<PileDTO> list() {
        return pileService.listPiles();
    }

    @PostMapping("/{pileId}/fault")
    public PileFaultResult markFault(@PathVariable Long pileId,
                                     @RequestBody(required = false) PileFaultRequest request) {
        String reason = request == null ? null : request.getFaultReason();
        return pileService.markFault(pileId, reason, request == null ? null : request.getChargedAmount());
    }

    @PostMapping("/{pileId}/recover")
    public PileRecoverResult recover(@PathVariable Long pileId) {
        return pileService.recover(pileId);
    }
}
