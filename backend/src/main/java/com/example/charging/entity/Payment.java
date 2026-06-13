package com.example.charging.entity;

import com.example.charging.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_bill", columnList = "bill_id"),
        @Index(name = "idx_user", columnList = "user_id"),
        @Index(name = "idx_status", columnList = "status")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    /** 使用通用支付状态枚举（见 PaymentStatus）*/
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
