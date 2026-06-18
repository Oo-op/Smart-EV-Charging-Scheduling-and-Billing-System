package com.example.charging.repository;

import com.example.charging.entity.ChargingPile;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingPileRepository extends JpaRepository<ChargingPile, Long> {

    Optional<ChargingPile> findByCode(String code);

    List<ChargingPile> findByStatus(ChargingPileStatus status);

    List<ChargingPile> findByStationId(Long stationId);

    List<ChargingPile> findByModeAndStatus(ChargeMode mode, ChargingPileStatus status);

    List<ChargingPile> findByModeAndStatusAndEnabledTrue(ChargeMode mode, ChargingPileStatus status);

    List<ChargingPile> findByModeAndEnabledTrue(ChargeMode mode);

    long countByModeAndStatus(ChargeMode mode, ChargingPileStatus status);

    long countByModeAndStatusAndEnabledTrue(ChargeMode mode, ChargingPileStatus status);

    long countByModeAndEnabledTrue(ChargeMode mode);

    long countByStatus(ChargingPileStatus status);
}
