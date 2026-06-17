package com.example.charging.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "vehicle", indexes = {
        @Index(name = "idx_vehicle_user", columnList = "user_id"),
        @Index(name = "idx_vehicle_plate", columnList = "plate_number")
})
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(length = 50)
    private String model;

    @Column(name = "battery_capacity", precision = 10, scale = 2)
    private BigDecimal batteryCapacity;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
