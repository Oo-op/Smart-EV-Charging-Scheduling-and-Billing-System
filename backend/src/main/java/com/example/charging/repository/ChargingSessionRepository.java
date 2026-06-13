package com.example.charging.repository;

import com.example.charging.entity.ChargingSession;
import com.example.charging.enums.ChargingSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {

    List<ChargingSession> findByUserId(Long userId);

    List<ChargingSession> findByPileId(Long pileId);

    List<ChargingSession> findByPileIdAndStatus(Long pileId, ChargingSessionStatus status);

    List<ChargingSession> findByRequestId(Long requestId);
}
