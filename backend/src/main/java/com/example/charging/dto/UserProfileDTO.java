package com.example.charging.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserProfileDTO {
    private Long userId;
    private String username;
    private String phone;
    private String role;
}
