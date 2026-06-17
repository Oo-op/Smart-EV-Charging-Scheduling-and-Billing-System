package com.example.charging.entity;

import com.example.charging.enums.ChargingSessionStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一次插枪到拔枪的充电会话。
 */
@Data
@Entity
@Table(name = "charging_session", indexes = {
        @Index(name = "idx_session_request", columnList = "request_id"),
        @Index(name = "idx_session_user", columnList = "user_id"),
        @Index(name = "idx_session_pile", columnList = "pile_id"),
        @Index(name = "idx_session_status", columnList = "status")
})
public class ChargingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "pile_id", nullable = false)
    private Long pileId;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "target_amount", precision = 10, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "charged_amount", precision = 10, scale = 2)
    private BigDecimal chargedAmount = BigDecimal.ZERO;

    /** CHARGING / COMPLETED / INTERRUPTED（见 ChargingSessionStatus） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargingSessionStatus status = ChargingSessionStatus.CHARGING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
