package com.example.charging.entity;

import com.example.charging.enums.BillStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "bill", indexes = {
        @Index(name = "idx_bill_user", columnList = "user_id"),
        @Index(name = "idx_bill_session", columnList = "session_id"),
        @Index(name = "idx_bill_status", columnList = "status")
})
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "electricity_fee", precision = 10, scale = 2)
    private BigDecimal electricityFee = BigDecimal.ZERO;

    @Column(name = "service_fee", precision = 10, scale = 2)
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(name = "total_fee", precision = 10, scale = 2)
    private BigDecimal totalFee = BigDecimal.ZERO;

    @Column(name = "breakdown", length = 512)
    private String breakdown;

    /** UNPAID / PAID / CANCELLED（见 BillStatus） */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillStatus status = BillStatus.UNPAID;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
