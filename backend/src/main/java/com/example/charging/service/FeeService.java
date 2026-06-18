package com.example.charging.service;

import com.example.charging.config.ChargingProperties;
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
    private final ChargingProperties chargingProperties;

    public FeeService(ElectricityPriceRepository priceRepository,
                      ChargingProperties chargingProperties) {
        this.priceRepository = priceRepository;
        this.chargingProperties = chargingProperties;
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

        // 1) 统计充电区间内各时段秒数
        Map<PricePeriod, Long> secondsPerPeriod = splitBySecond(
                req.getStartTime() != null ? req.getStartTime() : LocalDateTime.now(),
                req.getEndTime() != null ? req.getEndTime() : LocalDateTime.now()
        );

        long totalSeconds = secondsPerPeriod.values().stream().mapToLong(Long::longValue).sum();
        if (totalSeconds <= 0) {
            totalSeconds = 1;
        }

        // 2) 按秒比例把电量分配到各时段，并分别查表计费 (高精度计算，不提前四舍五入)
        StringBuilder breakdown = new StringBuilder();
        BigDecimal electricityFee = BigDecimal.ZERO;
        BigDecimal serviceFee = BigDecimal.ZERO;

        for (Map.Entry<PricePeriod, Long> entry : secondsPerPeriod.entrySet()) {
            PricePeriod period = entry.getKey();
            long seconds = entry.getValue();
            if (seconds <= 0) continue;

            BigDecimal ratio = BigDecimal.valueOf(seconds)
                    .divide(BigDecimal.valueOf(totalSeconds), 8, RoundingMode.HALF_UP);
            BigDecimal segEnergy = totalEnergy.multiply(ratio);

            ElectricityPrice price = findPrice(period, mode);
            BigDecimal segElec = segEnergy.multiply(price.getChargingFee());
            BigDecimal segSvc = segEnergy.multiply(price.getServiceFee());

            electricityFee = electricityFee.add(segElec);
            serviceFee = serviceFee.add(segSvc);

            breakdown.append(String.format("[%s %ds × %.2f度 @电%.2f/服%.2f] ",
                    period.name(), seconds, segEnergy.setScale(2, RoundingMode.HALF_UP),
                    price.getChargingFee(), price.getServiceFee()));
        }

        BigDecimal electricityFeeRounded = electricityFee.setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceFeeRounded = serviceFee.setScale(2, RoundingMode.HALF_UP);

        return FeeCalcResult.builder()
                .electricityFee(electricityFeeRounded)
                .serviceFee(serviceFeeRounded)
                .totalFee(electricityFeeRounded.add(serviceFeeRounded))
                .energyKwh(totalEnergy.setScale(2, RoundingMode.HALF_UP))
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
    private Map<PricePeriod, Long> splitBySecond(LocalDateTime start, LocalDateTime end) {
        Map<PricePeriod, Long> result = new EnumMap<>(PricePeriod.class);
        result.put(PricePeriod.PEAK, 0L);
        result.put(PricePeriod.FLAT, 0L);
        result.put(PricePeriod.VALLEY, 0L);

        if (end.isBefore(start)) return result;
        if (end.isEqual(start)) {
            result.put(getPeriodForHour(start.getHour()), 60L);
            return result;
        }

        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime nextHour = cursor.plusHours(1).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime stepEnd = nextHour.isBefore(end) ? nextHour : end;
            long seconds = Duration.between(cursor, stepEnd).toSeconds();
            result.merge(getPeriodForHour(cursor.getHour()), seconds, Long::sum);
            cursor = stepEnd;
        }
        return result;
    }

    private PricePeriod getPeriodForHour(int hour) {
        if (chargingProperties.getPeakHours().contains(hour)) {
            return PricePeriod.PEAK;
        }
        if (chargingProperties.getFlatHours().contains(hour)) {
            return PricePeriod.FLAT;
        }
        return PricePeriod.VALLEY;
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
