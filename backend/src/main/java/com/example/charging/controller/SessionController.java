package com.example.charging.controller;

import com.example.charging.dto.SessionDTO;
import com.example.charging.dto.StartSessionRequest;
import com.example.charging.dto.StopSessionRequest;
import com.example.charging.dto.StopSessionResult;
import com.example.charging.service.SessionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/start")
    public SessionDTO start(@RequestBody StartSessionRequest request) {
        return sessionService.start(request.getRequestId(), request.getPileId());
    }

    @PostMapping("/{sessionId}/stop")
    public StopSessionResult stop(@PathVariable Long sessionId,
                                  @RequestBody(required = false) StopSessionRequest request) {
        return sessionService.stop(sessionId, request == null ? null : request.getChargedAmount());
    }
}
