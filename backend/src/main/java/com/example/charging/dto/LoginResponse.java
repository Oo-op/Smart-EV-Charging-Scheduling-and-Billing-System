package com.example.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String username;
    private String role;
    private String token;  // 第一版 mock token
}