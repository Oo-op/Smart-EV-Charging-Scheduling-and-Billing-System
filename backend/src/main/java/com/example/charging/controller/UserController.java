package com.example.charging.controller;

import com.example.charging.dto.LoginRequest;
import com.example.charging.dto.LoginResponse;
import com.example.charging.dto.RegisterRequest;
import com.example.charging.entity.User;
import com.example.charging.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public User register(@RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return userService.login(request.getUsername(), request.getPassword());
    }
}