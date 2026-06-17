package com.example.charging.config;

import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ChargingStation;
import com.example.charging.entity.ElectricityPrice;
import com.example.charging.entity.User;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.PricePeriod;
import com.example.charging.enums.StationStatus;
import com.example.charging.repository.ChargingPileRepository;
import com.example.charging.repository.ChargingStationRepository;
import com.example.charging.repository.ElectricityPriceRepository;
import com.example.charging.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Component
public class DemoDataBootstrap implements ApplicationRunner {

    private final ChargingStationRepository stationRepository;
    private final ChargingPileRepository pileRepository;
    private final ElectricityPriceRepository priceRepository;
    private final UserRepository userRepository;

    public DemoDataBootstrap(ChargingStationRepository stationRepository,
                             ChargingPileRepository pileRepository,
                             ElectricityPriceRepository priceRepository,
                             UserRepository userRepository) {
        this.stationRepository = stationRepository;
        this.pileRepository = pileRepository;
        this.priceRepository = priceRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureCoreData();
        ensureDefaultAdmin();
    }

    private void ensureCoreData() {
        if (stationRepository.count() == 0) {
            ChargingStation station = new ChargingStation();
            station.setName("张江科创充电站");
            station.setAddress("上海市浦东新区张江高科技园区");
            station.setStatus(StationStatus.AVAILABLE.name());
            stationRepository.save(station);
        }

        Long stationId = stationRepository.findAll().get(0).getId();

        if (pileRepository.count() == 0) {
            createPile(stationId, "FAST-1", ChargeMode.FAST, "60", "0.70");
            createPile(stationId, "FAST-2", ChargeMode.FAST, "60", "0.70");
            createPile(stationId, "SLOW-1", ChargeMode.SLOW, "7", "0.40");
            createPile(stationId, "SLOW-2", ChargeMode.SLOW, "7", "0.40");
            createPile(stationId, "SLOW-3", ChargeMode.SLOW, "7", "0.40");
        }

        if (priceRepository.count() == 0) {
            List<Object[]> rows = Arrays.asList(
                    new Object[]{PricePeriod.PEAK, ChargeMode.FAST, new BigDecimal("1.20"), new BigDecimal("0.70"), "08:00-11:00, 18:00-21:00"},
                    new Object[]{PricePeriod.FLAT, ChargeMode.FAST, new BigDecimal("0.80"), new BigDecimal("0.70"), "12:00-17:00"},
                    new Object[]{PricePeriod.VALLEY, ChargeMode.FAST, new BigDecimal("0.40"), new BigDecimal("0.70"), "22:00-07:00"},
                    new Object[]{PricePeriod.PEAK, ChargeMode.SLOW, new BigDecimal("1.20"), new BigDecimal("0.40"), "08:00-11:00, 18:00-21:00"},
                    new Object[]{PricePeriod.FLAT, ChargeMode.SLOW, new BigDecimal("0.80"), new BigDecimal("0.40"), "12:00-17:00"},
                    new Object[]{PricePeriod.VALLEY, ChargeMode.SLOW, new BigDecimal("0.40"), new BigDecimal("0.40"), "22:00-07:00"},
                    new Object[]{PricePeriod.PEAK, ChargeMode.NORMAL, new BigDecimal("1.00"), new BigDecimal("0.50"), "08:00-11:00, 18:00-21:00"},
                    new Object[]{PricePeriod.FLAT, ChargeMode.NORMAL, new BigDecimal("0.70"), new BigDecimal("0.50"), "12:00-17:00"},
                    new Object[]{PricePeriod.VALLEY, ChargeMode.NORMAL, new BigDecimal("0.30"), new BigDecimal("0.50"), "22:00-07:00"}
            );

            for (Object[] row : rows) {
                ElectricityPrice price = new ElectricityPrice();
                price.setPeriod((PricePeriod) row[0]);
                price.setMode((ChargeMode) row[1]);
                price.setChargingFee((BigDecimal) row[2]);
                price.setServiceFee((BigDecimal) row[3]);
                price.setDescription((String) row[4]);
                priceRepository.save(price);
            }
        }
    }

    private void ensureDefaultAdmin() {
        userRepository.findByUsername("admin").orElseGet(() -> {
            User user = new User();
            user.setUsername("admin");
            user.setPassword("admin123456");
            user.setPhone("13800000000");
            user.setRole("ADMIN");
            return userRepository.save(user);
        });
    }

    private void createPile(Long stationId, String code, ChargeMode mode, String power, String serviceFee) {
        ChargingPile pile = new ChargingPile();
        pile.setStationId(stationId);
        pile.setCode(code);
        pile.setMode(mode);
        pile.setPower(new BigDecimal(power));
        pile.setStatus(ChargingPileStatus.IDLE);
        pile.setServiceFee(new BigDecimal(serviceFee));
        pileRepository.save(pile);
    }
}
