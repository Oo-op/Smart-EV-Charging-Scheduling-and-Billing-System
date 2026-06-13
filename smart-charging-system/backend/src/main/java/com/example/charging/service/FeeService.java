package com.example.charging.service;

import com.example.charging.dto.FeeCalcRequest;
import com.example.charging.dto.FeeCalcResult;
import com.example.charging.entity.ElectricityPrice;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.PricePeriod;
import com.example.charging.repository.ElectricityPriceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 计费服务：
 *   公式：费用 = 电量 × (charging_fee + service_fee)
 *   跨时段：按每小时所处时段(PEAK/FLAT/VALLEY)把电量比例拆分，分别计费后汇总。
 *   若表中未配置某(period, mode)，回退到平峰价格。
 */
@Service
public class FeeService {

    private final ElectricityPriceRepository priceRepository;

    public FeeService(ElectricityPriceRepository priceRepository) {
        this.priceRepository = priceRepository;
    }

    public FeeCalcResult calculate(FeeCalcRequest req) {
        BigDecimal totalEnergy = safe(req.getEnergyKwh());
        if (totalEnergy.compareTo(BigDecimal.ZERO) <= 0) {
            return FeeCalcResult.builder()
                    .electricityFee(BigDecimal.ZERO)
                    .serviceFee(BigDecimal.ZERO)
                    .totalFee(BigDecimal.ZERO)
                    .energyKwh(BigDecimal.ZERO)
                    .breakdown("无充电量")
                    .build();
        }

        ChargeMode mode = req.getMode();

        // 1) 统计充电区间内各时段小时数
        Map<PricePeriod, Long> hoursPerPeriod = splitByHour(
                req.getStartTime() != null ? req.getStartTime() : LocalDateTime.now(),
                req.getEndTime() != null ? req.getEndTime() : LocalDateTime.now()
        );

        long totalHours = hoursPerPeriod.values().stream().mapToLong(Long::longValue).sum();
        if (totalHours <= 0) {
            totalHours = 1;
        }

        // 2) 按小时比例把电量分配到各时段，并分别查表计费
        StringBuilder breakdown = new StringBuilder();
        BigDecimal electricityFee = BigDecimal.ZERO;
        BigDecimal serviceFee = BigDecimal.ZERO;

        for (Map.Entry<PricePeriod, Long> entry : hoursPerPeriod.entrySet()) {
            PricePeriod period = entry.getKey();
            long hours = entry.getValue();
            if (hours <= 0) continue;

            BigDecimal ratio = BigDecimal.valueOf(hours)
                    .divide(BigDecimal.valueOf(totalHours), 6, RoundingMode.HALF_UP);
            BigDecimal segEnergy = totalEnergy.multiply(ratio).setScale(2, RoundingMode.HALF_UP);

            ElectricityPrice price = findPrice(period, mode);
            BigDecimal segElec = segEnergy.multiply(price.getChargingFee())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal segSvc = segEnergy.multiply(price.getServiceFee())
                    .setScale(2, RoundingMode.HALF_UP);

            electricityFee = electricityFee.add(segElec);
            serviceFee = serviceFee.add(segSvc);

            breakdown.append(String.format("[%s %dh × %.2f度 @电%.2f/服%.2f] ",
                    period.name(), hours, segEnergy,
                    price.getChargingFee(), price.getServiceFee()));
        }

        return FeeCalcResult.builder()
                .electricityFee(electricityFee)
                .serviceFee(serviceFee)
                .totalFee(electricityFee.add(serviceFee))
                .energyKwh(totalEnergy)
                .breakdown(breakdown.toString().trim())
                .build();
    }

    /**
     * 简化：直接按指定时段 + 充电模式计算（当不关心跨时段时使用）。
     */
    public FeeCalcResult calculateByPeriod(ChargeMode mode,
                                           PricePeriod period,
                                           BigDecimal energyKwh) {
        BigDecimal kwh = safe(energyKwh);
        ElectricityPrice price = findPrice(period, mode);
        BigDecimal elec = kwh.multiply(price.getChargingFee()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal svc = kwh.multiply(price.getServiceFee()).setScale(2, RoundingMode.HALF_UP);
        return FeeCalcResult.builder()
                .electricityFee(elec)
                .serviceFee(svc)
                .totalFee(elec.add(svc))
                .energyKwh(kwh)
                .breakdown(String.format("%s/%s × %.2f度", period.name(), mode.name(), kwh))
                .build();
    }

    public List<ElectricityPrice> listPrices() {
        return priceRepository.findAll();
    }

    // ---- 辅助方法 ----

    private ElectricityPrice findPrice(PricePeriod period, ChargeMode mode) {
        Optional<ElectricityPrice> opt = priceRepository.findByPeriodAndMode(period, mode);
        if (opt.isPresent()) return opt.get();
        // 回退：平峰
        return priceRepository.findByPeriodAndMode(PricePeriod.FLAT, mode)
                .orElseThrow(() -> new IllegalStateException("no price configured: " + mode));
    }

    /**
     * 把 [start, end) 区间按小时拆分，统计每个时段(PEAK/FLAT/VALLEY)对应的小时数（按整小时估算）。
     * 若时间不足 1 小时，按 1 小时计。
     */
    private Map<PricePeriod, Long> splitByHour(LocalDateTime start, LocalDateTime end) {
        Map<PricePeriod, Long> result = new EnumMap<>(PricePeriod.class);
        result.put(PricePeriod.PEAK, 0L);
        result.put(PricePeriod.FLAT, 0L);
        result.put(PricePeriod.VALLEY, 0L);

        if (end.isBefore(start)) return result;

        long totalMinutes = Duration.between(start, end).toMinutes();
        if (totalMinutes == 0) {
            result.merge(PricePeriod.ofHour(start.getHour()), 1L, Long::sum);
            return result;
        }

        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime nextHour = cursor.plusHours(1).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime stepEnd = nextHour.isBefore(end) ? nextHour : end;
            long minutes = Duration.between(cursor, stepEnd).toMinutes();
            long hours = Math.max(1, Math.round(minutes / 60.0));
            result.merge(PricePeriod.ofHour(cursor.getHour()), hours, Long::sum);
            cursor = stepEnd;
        }
        return result;
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
