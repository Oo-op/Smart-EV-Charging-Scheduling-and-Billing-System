package com.example.charging.controller;

import com.example.charging.dto.AdminDashboardDTO;
import com.example.charging.dto.AdminPileDTO;
import com.example.charging.dto.AdminQueueDTO;
import com.example.charging.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public AdminDashboardDTO dashboard() {
        return adminService.getDashboard();
    }

    @GetMapping("/piles")
    public List<AdminPileDTO> piles() {
        return adminService.listPilesWithSessions();
    }

    @GetMapping("/queue")
    public AdminQueueDTO queue() {
        return adminService.getQueueOverview();
    }
}
