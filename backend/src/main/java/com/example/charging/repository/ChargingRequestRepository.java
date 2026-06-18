package com.example.charging.repository;
import java.util.Optional;

import com.example.charging.entity.ChargingRequest;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.enums.QueueArea;
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

    List<ChargingRequest> findByModeAndStatusAndQueueAreaOrderByCreatedAtAsc(
            ChargeMode mode, ChargingRequestStatus status, QueueArea queueArea);

    List<ChargingRequest> findByAssignedPileIdAndStatusInOrderByQueueNumberAscCreatedAtAsc(
            Long assignedPileId, List<ChargingRequestStatus> statuses);

    long countByQueueArea(QueueArea queueArea);

    // 在 ChargingRequestRepository 接口中添加以下方法

    Optional<ChargingRequest> findByIdAndUserId(Long id, Long userId);

    List<ChargingRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ChargingRequest> findByStatusAndAssignedAtLessThanEqual(
            ChargingRequestStatus status, java.time.LocalDateTime threshold);
}
