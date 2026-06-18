package com.example.charging.controller;

import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ElectricityPrice;
import com.example.charging.entity.ChargingStation;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.PricePeriod;
import com.example.charging.repository.ChargingPileRepository;
import com.example.charging.repository.ElectricityPriceRepository;
import com.example.charging.repository.ChargingStationRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * 系统初始化接口：第一次启动时调用，即可生成
 *   - 充电站 1 个
 *   - 充电桩：快充 2 台（60 kW，服务费 0.7/度）
 *              慢充 3 台（7  kW，服务费 0.4/度）
 *   - 电价（峰/平/谷 × 快充/慢充/普通）
 */
@RestController
@RequestMapping("/init")
public class InitController {

    private final ChargingStationRepository stationRepository;
    private final ChargingPileRepository pileRepository;
    private final ElectricityPriceRepository priceRepository;

    public InitController(ChargingStationRepository stationRepository,
                          ChargingPileRepository pileRepository,
                          ElectricityPriceRepository priceRepository) {
        this.stationRepository = stationRepository;
        this.pileRepository = pileRepository;
        this.priceRepository = priceRepository;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> init(@RequestBody(required = false) InitOptions opts) {
        InitOptions options = opts != null ? opts : new InitOptions();

        long existingStations = stationRepository.count();
        long existingPiles = pileRepository.count();
        long existingPrices = priceRepository.count();

        if (existingStations == 0) {
            ChargingStation station = new ChargingStation();
            station.setName("演示充电站");
            station.setAddress("上海市浦东新区");
            station.setStatus(com.example.charging.enums.StationStatus.AVAILABLE.name());
            stationRepository.save(station);
        }

        Long stationId = stationRepository.findAll().get(0).getId();

        if (existingPiles == 0 || options.isForcePiles()) {
            if (options.isForcePiles()) {
                pileRepository.deleteAll();
            }
            int fastCount = options.getFastPiles() > 0 ? options.getFastPiles() : 2;
            int slowCount = options.getSlowPiles() > 0 ? options.getSlowPiles() : 3;
            BigDecimal fastPower = options.getFastPower() != null ? options.getFastPower() : new BigDecimal("60");
            BigDecimal slowPower = options.getSlowPower() != null ? options.getSlowPower() : new BigDecimal("7");
            BigDecimal fastService = options.getFastServiceFee() != null ? options.getFastServiceFee() : new BigDecimal("0.70");
            BigDecimal slowService = options.getSlowServiceFee() != null ? options.getSlowServiceFee() : new BigDecimal("0.40");

            for (int i = 1; i <= fastCount; i++) {
                ChargingPile p = new ChargingPile();
                p.setStationId(stationId);
                p.setCode("FAST-" + i);
                p.setMode(ChargeMode.FAST);
                p.setPower(fastPower);
                p.setStatus(ChargingPileStatus.IDLE);
                p.setServiceFee(fastService);
                pileRepository.save(p);
            }
            for (int i = 1; i <= slowCount; i++) {
                ChargingPile p = new ChargingPile();
                p.setStationId(stationId);
                p.setCode("SLOW-" + i);
                p.setMode(ChargeMode.SLOW);
                p.setPower(slowPower);
                p.setStatus(ChargingPileStatus.IDLE);
                p.setServiceFee(slowService);
                pileRepository.save(p);
            }
        }

        if (existingPrices == 0 || options.isForcePrices()) {
            if (options.isForcePrices()) {
                priceRepository.deleteAll();
            }
            List<Object[]> rows = Arrays.asList(
                    new Object[]{PricePeriod.PEAK,   ChargeMode.FAST,   new BigDecimal("1.20"), new BigDecimal("0.70"), "08-11, 18-21"},
                    new Object[]{PricePeriod.FLAT,   ChargeMode.FAST,   new BigDecimal("0.80"), new BigDecimal("0.70"), "12-17"},
                    new Object[]{PricePeriod.VALLEY, ChargeMode.FAST,   new BigDecimal("0.40"), new BigDecimal("0.70"), "22-07"},
                    new Object[]{PricePeriod.PEAK,   ChargeMode.SLOW,   new BigDecimal("1.20"), new BigDecimal("0.40"), "08-11, 18-21"},
                    new Object[]{PricePeriod.FLAT,   ChargeMode.SLOW,   new BigDecimal("0.80"), new BigDecimal("0.40"), "12-17"},
                    new Object[]{PricePeriod.VALLEY, ChargeMode.SLOW,   new BigDecimal("0.40"), new BigDecimal("0.40"), "22-07"},
                    new Object[]{PricePeriod.PEAK,   ChargeMode.NORMAL, new BigDecimal("1.00"), new BigDecimal("0.50"), "08-11, 18-21"},
                    new Object[]{PricePeriod.FLAT,   ChargeMode.NORMAL, new BigDecimal("0.70"), new BigDecimal("0.50"), "12-17"},
                    new Object[]{PricePeriod.VALLEY, ChargeMode.NORMAL, new BigDecimal("0.30"), new BigDecimal("0.50"), "22-07"}
            );
            for (Object[] row : rows) {
                ElectricityPrice p = new ElectricityPrice();
                p.setPeriod((PricePeriod) row[0]);
                p.setMode((ChargeMode) row[1]);
                p.setChargingFee((BigDecimal) row[2]);
                p.setServiceFee((BigDecimal) row[3]);
                p.setDescription((String) row[4]);
                priceRepository.save(p);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stations", stationRepository.count());
        result.put("piles", pileRepository.count());
        result.put("prices", priceRepository.count());
        return result;
    }

    public static class InitOptions {
        private int fastPiles;
        private int slowPiles;
        private BigDecimal fastPower;
        private BigDecimal slowPower;
        private BigDecimal fastServiceFee;
        private BigDecimal slowServiceFee;
        private boolean forcePiles;
        private boolean forcePrices;

        public int getFastPiles() { return fastPiles; }
        public void setFastPiles(int fastPiles) { this.fastPiles = fastPiles; }
        public int getSlowPiles() { return slowPiles; }
        public void setSlowPiles(int slowPiles) { this.slowPiles = slowPiles; }
        public BigDecimal getFastPower() { return fastPower; }
        public void setFastPower(BigDecimal fastPower) { this.fastPower = fastPower; }
        public BigDecimal getSlowPower() { return slowPower; }
        public void setSlowPower(BigDecimal slowPower) { this.slowPower = slowPower; }
        public BigDecimal getFastServiceFee() { return fastServiceFee; }
        public void setFastServiceFee(BigDecimal fastServiceFee) { this.fastServiceFee = fastServiceFee; }
        public BigDecimal getSlowServiceFee() { return slowServiceFee; }
        public void setSlowServiceFee(BigDecimal slowServiceFee) { this.slowServiceFee = slowServiceFee; }
        public boolean isForcePiles() { return forcePiles; }
        public void setForcePiles(boolean forcePiles) { this.forcePiles = forcePiles; }
        public boolean isForcePrices() { return forcePrices; }
        public void setForcePrices(boolean forcePrices) { this.forcePrices = forcePrices; }
    }
}
