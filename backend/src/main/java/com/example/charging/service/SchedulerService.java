package com.example.charging.service;

import com.example.charging.dto.DispatchResult;
import com.example.charging.dto.QueueItemDTO;
import com.example.charging.dto.QueueStatusDTO;
import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ChargingRequest;
import com.example.charging.entity.Vehicle;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.repository.ChargingPileRepository;
import com.example.charging.repository.ChargingRequestRepository;
import com.example.charging.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SchedulerService {

    private static final String ALGORITHM = "SJF";
    private static final String TEMP_PRIORITY_NOTE = "断点续充优先级临时复用 queueNumber=0 表示；等待 B 模块提供正式队列/优先级字段后可替换。";

    private final ChargingRequestRepository requestRepository;
    private final ChargingPileRepository pileRepository;
    private final VehicleRepository vehicleRepository;

    public SchedulerService(ChargingRequestRepository requestRepository,
                            ChargingPileRepository pileRepository,
                            VehicleRepository vehicleRepository) {
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional
    public DispatchResult dispatch(ChargeMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("充电模式不能为空");
        }

        ChargingPile pile = pileRepository.findByModeAndStatus(mode, ChargingPileStatus.IDLE)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前模式下没有空闲充电桩"));

        ChargingRequest request = selectNextRequest(mode, pile);

        request.setStatus(ChargingRequestStatus.ASSIGNED);
        request.setAssignedPileId(pile.getId());
        pile.setStatus(ChargingPileStatus.RESERVED);
        requestRepository.save(request);
        pileRepository.save(pile);

        DispatchResult result = new DispatchResult();
        result.setAlgorithm(ALGORITHM);
        result.setRequestId(request.getId());
        result.setPileId(pile.getId());
        result.setPileCode(pile.getCode());
        result.setMode(mode);
        result.setRemainingAmount(remainingAmount(request));
        result.setEstimatedDurationMinutes(estimatedDurationMinutes(request, pile));
        result.setRequestStatus(request.getStatus());
        result.setPileStatus(pile.getStatus());
        result.setEstimatedStartTime(LocalDateTime.now());
        if (isRecoveryPriority(request)) {
            result.setTemporarySimulationNote(TEMP_PRIORITY_NOTE);
        }
        return result;
    }

    public QueueStatusDTO getQueue(ChargeMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("充电模式不能为空");
        }

        List<ChargingRequest> waiting = requestRepository
                .findByModeAndStatusOrderByCreatedAtAsc(mode, ChargingRequestStatus.WAITING);
        ChargingPile referencePile = pileRepository.findByModeAndStatus(mode, ChargingPileStatus.IDLE)
                .stream()
                .findFirst()
                .orElse(null);
        Map<Long, Vehicle> vehicles = vehicleRepository.findAllById(
                        waiting.stream().map(ChargingRequest::getVehicleId).toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));

        List<QueueItemDTO> items = waiting.stream()
                .sorted(dispatchComparator(referencePile))
                .map(request -> toQueueItem(request, vehicles.get(request.getVehicleId()), referencePile))
                .toList();

        long availablePileCount = pileRepository.countByModeAndStatus(mode, ChargingPileStatus.IDLE);
        QueueStatusDTO dto = new QueueStatusDTO();
        dto.setAlgorithm(ALGORITHM);
        dto.setMode(mode);
        dto.setQueueLength(items.size());
        dto.setAvailablePileCount((int) availablePileCount);
        dto.setEstimatedWaitTime(0);
        dto.setTemporarySimulationNote(TEMP_PRIORITY_NOTE);
        dto.setWaitingList(items);
        return dto;
    }

    private ChargingRequest selectNextRequest(ChargeMode mode, ChargingPile pile) {
        return requestRepository
                .findByModeAndStatusOrderByCreatedAtAsc(mode, ChargingRequestStatus.WAITING)
                .stream()
                .min(dispatchComparator(pile))
                .orElseThrow(() -> new IllegalArgumentException("当前模式下没有等待请求"));
    }

    private Comparator<ChargingRequest> dispatchComparator(ChargingPile pile) {
        return Comparator
                .comparing((ChargingRequest request) -> !isRecoveryPriority(request))
                .thenComparing(request -> estimatedDurationMinutes(request, pile))
                .thenComparing(ChargingRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private QueueItemDTO toQueueItem(ChargingRequest request, Vehicle vehicle, ChargingPile referencePile) {
        QueueItemDTO dto = new QueueItemDTO();
        dto.setRequestId(request.getId());
        dto.setVehicleId(request.getVehicleId());
        dto.setPlateNumber(vehicle == null ? null : vehicle.getPlateNumber());
        dto.setTargetAmount(request.getTargetAmount());
        dto.setChargedAmount(safe(request.getChargedAmount()));
        dto.setRemainingAmount(remainingAmount(request));
        dto.setEstimatedDurationMinutes(estimatedDurationMinutes(request, referencePile));
        dto.setQueueNumber(request.getQueueNumber());
        dto.setRecoveryPriority(isRecoveryPriority(request));
        dto.setStatus(request.getStatus());
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }

    private boolean isRecoveryPriority(ChargingRequest request) {
        return request.getQueueNumber() != null && request.getQueueNumber() <= 0;
    }

    private BigDecimal remainingAmount(ChargingRequest request) {
        BigDecimal remaining = safe(request.getTargetAmount()).subtract(safe(request.getChargedAmount()));
        return remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private Long estimatedDurationMinutes(ChargingRequest request, ChargingPile pile) {
        if (pile == null || safe(pile.getPower()).compareTo(BigDecimal.ZERO) <= 0) {
            return Long.MAX_VALUE;
        }
        return remainingAmount(request)
                .multiply(BigDecimal.valueOf(60))
                .divide(pile.getPower(), 0, RoundingMode.CEILING)
                .longValue();
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
