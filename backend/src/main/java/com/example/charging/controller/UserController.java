package com.example.charging.controller;

import com.example.charging.dto.ChargingRequestDetailDTO;
import com.example.charging.dto.LoginRequest;
import com.example.charging.dto.LoginResponse;
import com.example.charging.dto.RegisterRequest;
import com.example.charging.entity.User;
import com.example.charging.service.ChargingRequestService;
import com.example.charging.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final ChargingRequestService chargingRequestService;

    public UserController(UserService userService, ChargingRequestService chargingRequestService) {
        this.userService = userService;
        this.chargingRequestService = chargingRequestService;
    }

    @PostMapping("/register")
    public User register(@RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return userService.login(request.getUsername(), request.getPassword());
    }

    /** 与 GET /users/{userId}/vehicles 同风格，供用户端「我的订单」列表 */
    @GetMapping("/{userId}/charging-requests")
    public List<ChargingRequestDetailDTO> listChargingRequests(@PathVariable Long userId) {
        return chargingRequestService.listByUserId(userId);
    }
}