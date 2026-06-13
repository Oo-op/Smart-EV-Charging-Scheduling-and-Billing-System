package com.example.charging.controller;

import com.example.charging.dto.VehicleBindRequest;
import com.example.charging.dto.VehicleDTO;
import com.example.charging.service.VehicleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping("/vehicles")
    public VehicleDTO bindVehicle(@RequestBody VehicleBindRequest request) {
        return vehicleService.bindVehicle(request);
    }

    @GetMapping("/users/{userId}/vehicles")
    public List<VehicleDTO> listUserVehicles(@PathVariable Long userId) {
        return vehicleService.listVehiclesByUser(userId);
    }
}