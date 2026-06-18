package com.example.charging.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminDashboardDTO {

    private long totalUsers;
    private long totalVehicles;
    private long totalRequests;
    private long activeSessions;
    private BigDecimal todayRevenue;
    private PileSummaryDTO pileSummary;
    private AdminCapacityOverviewDTO capacityOverview;
}
