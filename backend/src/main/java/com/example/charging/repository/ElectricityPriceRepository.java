package com.example.charging.repository;

import com.example.charging.entity.ElectricityPrice;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.PricePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ElectricityPriceRepository extends JpaRepository<ElectricityPrice, Long> {

    Optional<ElectricityPrice> findByPeriodAndMode(PricePeriod period, ChargeMode mode);

    List<ElectricityPrice> findByMode(ChargeMode mode);
}
