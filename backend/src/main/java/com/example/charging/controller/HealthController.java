package com.example.charging.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * 返回值会被 GlobalResponseHandler 自动包装为：
     * { "code": 0, "message": "success", "data": { "status": "UP", "service": "..." } }
     */
    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("service", "smart-charging-system");
        return data;
    }
}
