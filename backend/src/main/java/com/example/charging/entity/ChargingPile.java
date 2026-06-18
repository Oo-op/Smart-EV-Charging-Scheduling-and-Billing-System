package com.example.charging.entity;

import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "charging_pile", indexes = {
        @Index(name = "idx_pile_station", columnList = "station_id"),
        @Index(name = "idx_pile_mode", columnList = "mode"),
        @Index(name = "idx_pile_status", columnList = "status")
})
public class ChargingPile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id")
    private Long stationId;

    @Column(nullable = false, length = 50, unique = true)
    private String code;

    /** 充电模式：FAST / SLOW。NORMAL / SMART / SCHEDULED 预留第二版 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeMode mode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal power;

    /** IDLE / RESERVED / CHARGING / FAULT / OFFLINE（见 ChargingPileStatus） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargingPileStatus status = ChargingPileStatus.IDLE;

    @Column(name = "service_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "open_queue_slots", nullable = false)
    private Integer openQueueSlots = 3;

    @Column(name = "max_queue_slots", nullable = false)
    private Integer maxQueueSlots = 3;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
