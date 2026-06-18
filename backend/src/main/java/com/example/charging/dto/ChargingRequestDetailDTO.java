package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.enums.QueueArea;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChargingRequestDetailDTO {
    private Long requestId;
    private Long userId;
    private Long vehicleId;
    private ChargeMode mode;
    private BigDecimal targetAmount;
    private BigDecimal chargedAmount;
    private ChargingRequestStatus status;
    private Integer queueNumber;
    private QueueArea queueArea;
    private Long assignedPileId;
    private LocalDateTime createdAt;
    /** 分配桩位时间（status=ASSIGNED 时有值） */
    private LocalDateTime assignedAt;
    /** 超时自动开始充电截止时间（assignedAt + timeoutMinutes） */
    private LocalDateTime autoStartAt;
    /** 与后端 charging.assignment.timeout-minutes 一致 */
    private Integer assignmentTimeoutMinutes;
    /** 进行中会话 ID（status=CHARGING 时有值） */
    private Long activeSessionId;
    /** 列表展示用：关联车辆车牌 */
    private String plateNumber;
}
