package com.example.charging.entity;

import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingRequestStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户侧发起的充电请求。
 * 第一版的排队直接从本表按 mode + status IN (WAITING, ASSIGNED) + created_at 查询。
 */
@Data
@Entity
@Table(name = "charging_request", indexes = {
        @Index(name = "idx_user", columnList = "user_id"),
        @Index(name = "idx_vehicle", columnList = "vehicle_id"),
        @Index(name = "idx_mode_status", columnList = "mode, status"),
        @Index(name = "idx_created", columnList = "created_at")
})
public class ChargingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeMode mode;

    @Column(name = "target_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "charged_amount", precision = 10, scale = 2)
    private BigDecimal chargedAmount = BigDecimal.ZERO;

    /** WAITING / ASSIGNED / CHARGING / CANCELLED / COMPLETED（见 ChargingRequestStatus） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargingRequestStatus status = ChargingRequestStatus.WAITING;

    @Column(name = "queue_number")
    private Integer queueNumber;

    @Column(name = "assigned_pile_id")
    private Long assignedPileId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
