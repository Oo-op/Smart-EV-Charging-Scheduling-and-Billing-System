package com.example.charging.controller;

import com.example.charging.dto.AdminDashboardDTO;
import com.example.charging.dto.AdminPileDTO;
import com.example.charging.dto.AdminQueueDTO;
import com.example.charging.service.AdminService;
import com.example.charging.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    public AdminController(AdminService adminService, UserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardDTO dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return adminService.getDashboard();
    }

    @GetMapping("/piles")
    public List<AdminPileDTO> piles(@RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return adminService.listPilesWithSessions();
    }

    @GetMapping("/queue")
    public AdminQueueDTO queue(@RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return adminService.getQueueOverview();
    }
}
