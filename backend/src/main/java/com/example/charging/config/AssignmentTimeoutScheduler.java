package com.example.charging.config;

import com.example.charging.service.SessionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssignmentTimeoutScheduler {

    private final SessionService sessionService;

    public AssignmentTimeoutScheduler(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** 每 30 秒扫描一次：分配桩位超过时限未插枪则自动开始充电 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void autoStartExpiredAssignments() {
        sessionService.autoStartTimedOutAssignments();
    }
}
