package com.example.charging.repository;

import com.example.charging.entity.ChargingRequest;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 第一版排队：mode + status IN (WAITING, ASSIGNED) 按 created_at 升序。
 */
@Repository
public interface ChargingRequestRepository extends JpaRepository<ChargingRequest, Long> {

    List<ChargingRequest> findByUserId(Long userId);

    List<ChargingRequest> findByModeAndStatusOrderByCreatedAtAsc(ChargeMode mode, ChargingRequestStatus status);

    List<ChargingRequest> findByModeAndStatusInOrderByCreatedAtAsc(ChargeMode mode, List<ChargingRequestStatus> statuses);

    List<ChargingRequest> findByStatus(ChargingRequestStatus status);
}
