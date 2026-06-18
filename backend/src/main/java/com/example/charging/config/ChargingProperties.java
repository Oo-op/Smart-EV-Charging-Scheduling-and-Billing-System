package com.example.charging.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "charging")
public class ChargingProperties {

    private PriceProperties price = new PriceProperties();
    private PowerProperties power = new PowerProperties();
    private PileCountProperties pileCount = new PileCountProperties();
    private QueueProperties queue = new QueueProperties();
    private List<Integer> peakHours = Arrays.asList(8, 9, 10, 11, 18, 19, 20, 21);
    private List<Integer> flatHours = Arrays.asList(12, 13, 14, 15, 16, 17);

    @Data
    public static class PriceProperties {
        private BigDecimal peak = new BigDecimal("1.20");
        private BigDecimal normal = new BigDecimal("0.80");
        private BigDecimal valley = new BigDecimal("0.40");
        private BigDecimal service = new BigDecimal("0.70");
    }

    @Data
    public static class PowerProperties {
        private BigDecimal fast = new BigDecimal("60.00");
        private BigDecimal slow = new BigDecimal("7.00");
    }

    @Data
    public static class PileCountProperties {
        private int fast = 2;
        private int slow = 3;
    }

    @Data
    public static class QueueProperties {
        private int pileCapacity = 2;
        private int waitingAreaCapacity = 10;
    }

    @Data
    public static class AssignmentProperties {
        /** 分配桩位后未插枪，自动开始充电的等待分钟数 */
        private int timeoutMinutes = 5;
    }

    private AssignmentProperties assignment = new AssignmentProperties();
}
