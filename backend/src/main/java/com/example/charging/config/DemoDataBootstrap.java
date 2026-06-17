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
    private final ChargingProperties chargingProperties;

    public DemoDataBootstrap(ChargingStationRepository stationRepository,
                             ChargingPileRepository pileRepository,
                             ElectricityPriceRepository priceRepository,
                             UserRepository userRepository,
                             ChargingProperties chargingProperties) {
        this.stationRepository = stationRepository;
        this.pileRepository = pileRepository;
        this.priceRepository = priceRepository;
        this.userRepository = userRepository;
        this.chargingProperties = chargingProperties;
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
            String fastPower = chargingProperties.getPower().getFast().toString();
            String slowPower = chargingProperties.getPower().getSlow().toString();
            String serviceFee = chargingProperties.getPrice().getService().toString();

            createPile(stationId, "FAST-1", ChargeMode.FAST, fastPower, serviceFee);
            createPile(stationId, "FAST-2", ChargeMode.FAST, fastPower, serviceFee);
            createPile(stationId, "SLOW-1", ChargeMode.SLOW, slowPower, serviceFee);
            createPile(stationId, "SLOW-2", ChargeMode.SLOW, slowPower, serviceFee);
            createPile(stationId, "SLOW-3", ChargeMode.SLOW, slowPower, serviceFee);
        }

        if (priceRepository.count() == 0) {
            BigDecimal serviceFee = chargingProperties.getPrice().getService();
            List<Object[]> rows = Arrays.asList(
                    new Object[]{PricePeriod.PEAK, ChargeMode.FAST, chargingProperties.getPrice().getPeak(), serviceFee, "Peak Period"},
                    new Object[]{PricePeriod.FLAT, ChargeMode.FAST, chargingProperties.getPrice().getNormal(), serviceFee, "Flat Period"},
                    new Object[]{PricePeriod.VALLEY, ChargeMode.FAST, chargingProperties.getPrice().getValley(), serviceFee, "Valley Period"},
                    new Object[]{PricePeriod.PEAK, ChargeMode.SLOW, chargingProperties.getPrice().getPeak(), serviceFee, "Peak Period"},
                    new Object[]{PricePeriod.FLAT, ChargeMode.SLOW, chargingProperties.getPrice().getNormal(), serviceFee, "Flat Period"},
                    new Object[]{PricePeriod.VALLEY, ChargeMode.SLOW, chargingProperties.getPrice().getValley(), serviceFee, "Valley Period"},
                    new Object[]{PricePeriod.PEAK, ChargeMode.NORMAL, chargingProperties.getPrice().getPeak(), serviceFee, "Peak Period"},
                    new Object[]{PricePeriod.FLAT, ChargeMode.NORMAL, chargingProperties.getPrice().getNormal(), serviceFee, "Flat Period"},
                    new Object[]{PricePeriod.VALLEY, ChargeMode.NORMAL, chargingProperties.getPrice().getValley(), serviceFee, "Valley Period"}
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