package com.example.charging.entity;

import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.PricePeriod;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "electricity_price", uniqueConstraints = {
        @UniqueConstraint(name = "uk_period_mode", columnNames = {"period", "mode"})
})
public class ElectricityPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PEAK / FLAT / VALLEY */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PricePeriod period;

    /** FAST / NORMAL / SLOW */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargeMode mode;

    /** 元/度 */
    @Column(name = "charging_fee", nullable = false, precision = 10, scale = 4)
    private BigDecimal chargingFee;

    /** 元/度 */
    @Column(name = "service_fee", nullable = false, precision = 10, scale = 4)
    private BigDecimal serviceFee;

    @Column(length = 128)
    private String description;

    public BigDecimal totalFeePerKwh() {
        return chargingFee.add(serviceFee);
    }
}
