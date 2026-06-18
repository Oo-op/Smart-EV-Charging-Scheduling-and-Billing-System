package com.example.charging.service;

import com.example.charging.config.ChargingProperties;
import com.example.charging.dto.DispatchResult;
import com.example.charging.dto.QueueItemDTO;
import com.example.charging.dto.QueueStatusDTO;
import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ChargingRequest;
import com.example.charging.entity.ChargingSession;
import com.example.charging.entity.Vehicle;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.enums.ChargingSessionStatus;
import com.example.charging.enums.QueueArea;
import com.example.charging.repository.ChargingPileRepository;
import com.example.charging.repository.ChargingRequestRepository;
import com.example.charging.repository.ChargingSessionRepository;
import com.example.charging.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SchedulerService {

    private static final String ALGORITHM = "SJF";
    private static final List<ChargingRequestStatus> PILE_QUEUE_STATUSES =
            List.of(ChargingRequestStatus.WAITING, ChargingRequestStatus.ASSIGNED);

    private final ChargingRequestRepository requestRepository;
    private final ChargingPileRepository pileRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargingSessionRepository sessionRepository;
    private final ChargingProperties chargingProperties;

    public SchedulerService(ChargingRequestRepository requestRepository,
                            ChargingPileRepository pileRepository,
                            VehicleRepository vehicleRepository,
                            ChargingSessionRepository sessionRepository,
                            ChargingProperties chargingProperties) {
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.vehicleRepository = vehicleRepository;
        this.sessionRepository = sessionRepository;
        this.chargingProperties = chargingProperties;
    }

    @Transactional
    public DispatchResult dispatch(ChargeMode mode) {
        List<DispatchResult> results = triggerDispatch(mode);
        return results.stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No dispatchable request for current mode"));
    }

    @Transactional
    public ChargingRequest enqueueNewRequest(Long requestId) {
        ChargingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Charging request not found"));
        if (request.getStatus() == ChargingRequestStatus.WAITING) {
            markAsWaitingArea(request);
            triggerDispatch(request.getMode());
            request = requestRepository.findById(requestId).orElseThrow();
            if (request.getStatus() == ChargingRequestStatus.WAITING
                    && request.getQueueArea() == QueueArea.WAITING_AREA) {
                assignToBestAvailablePileQueue(request);
            }
        }
        return requestRepository.findById(requestId).orElseThrow();
    }

    @Transactional
    public List<DispatchResult> redispatchWaitingArea(ChargeMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Charging mode cannot be null");
        }
        List<ChargingRequest> waitingRequests = requestRepository
                .findByModeAndStatusOrderByCreatedAtAsc(mode, ChargingRequestStatus.WAITING);
        for (ChargingRequest request : waitingRequests) {
            if (request.getQueueArea() == QueueArea.RECOVERY_QUEUE
                    || request.getQueueArea() == QueueArea.MIGRATION_QUEUE) {
                continue;
            }
            markAsWaitingArea(request);
        }
        releaseOrphanReservedPiles(mode);
        return triggerDispatch(mode);
    }

    @Transactional
    public List<DispatchResult> triggerDispatch(ChargeMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Charging mode cannot be null");
        }

        List<DispatchResult> assigned = new ArrayList<>();
        while (true) {
            ChargingRequest next = nextWaitingRequest(mode).orElse(null);
            if (next == null) {
                reserveIdlePileHeads(mode, assigned);
                return assigned;
            }

            ChargingPile targetPile = bestCandidatePile(mode, next).orElse(null);
            if (targetPile == null) {
                reserveIdlePileHeads(mode, assigned);
                return assigned;
            }

            if (!moveToPileQueue(next, targetPile)) {
                if (next.getQueueArea() == QueueArea.WAITING_AREA) {
                    markAsWaitingArea(next);
                }
                reserveIdlePileHeads(mode, assigned);
                return assigned;
            }
            if (next.getStatus() == ChargingRequestStatus.ASSIGNED) {
                assigned.add(toDispatchResult(next, targetPile));
            }
        }
    }

    @Transactional
    public DispatchResult promoteNextForPile(Long pileId) {
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new IllegalArgumentException("Charging pile not found"));
        List<DispatchResult> results = triggerDispatch(pile.getMode());
        return results.stream()
                .filter(result -> result.getPileId().equals(pileId))
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public void onPileQueueSlotFreed(Long pileId) {
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new IllegalArgumentException("Charging pile not found"));
        triggerPriorityDispatch(pile.getMode());
    }

    @Transactional
    public void removeFromPileQueueAndRedispatch(ChargingRequest request) {
        ChargeMode mode = request.getMode();
        markAsWaitingArea(request);
        triggerDispatch(mode);
    }

    @Transactional
    public void migrateFaultedPileQueue(Long faultedPileId) {
        ChargingPile pile = pileRepository.findById(faultedPileId)
                .orElseThrow(() -> new IllegalArgumentException("Charging pile not found"));
        List<ChargingRequest> queued = pileQueueRequests(faultedPileId);
        int order = 1;
        for (ChargingRequest request : queued) {
            request.setStatus(ChargingRequestStatus.WAITING);
            request.setAssignedPileId(null);
            request.setAssignedAt(null);
            request.setQueueArea(QueueArea.MIGRATION_QUEUE);
            request.setQueueNumber(order++);
            requestRepository.save(request);
        }
        triggerPriorityDispatch(pile.getMode());
    }

    @Transactional
    public void placeRecoveryRequest(Long requestId) {
        ChargingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Charging request not found"));
        request.setStatus(ChargingRequestStatus.WAITING);
        request.setAssignedPileId(null);
        request.setAssignedAt(null);
        request.setQueueArea(QueueArea.RECOVERY_QUEUE);
        request.setQueueNumber(0);
        requestRepository.save(request);
        triggerPriorityDispatch(request.getMode());
    }

    public QueueStatusDTO getQueue(ChargeMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Charging mode cannot be null");
        }

        List<ChargingRequest> waiting = requestRepository
                .findByModeAndStatusOrderByCreatedAtAsc(mode, ChargingRequestStatus.WAITING);
        ChargingPile referencePile = pileRepository.findByModeAndStatusAndEnabledTrue(mode, ChargingPileStatus.IDLE)
                .stream()
                .filter(this::isOpenForQueue)
                .findFirst()
                .orElse(null);
        Map<Long, Vehicle> vehicles = vehicleRepository.findAllById(
                        waiting.stream().map(ChargingRequest::getVehicleId).toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));

        List<QueueItemDTO> items = waiting.stream()
                .sorted(queueViewComparator())
                .map(request -> toQueueItem(request, vehicles.get(request.getVehicleId()), referencePile))
                .toList();

        int totalOpenSlots = totalOpenQueueSlots(mode);
        long availablePileCount = pileRepository.findByModeAndStatusAndEnabledTrue(mode, ChargingPileStatus.IDLE)
                .stream()
                .filter(this::isOpenForQueue)
                .count();

        QueueStatusDTO dto = new QueueStatusDTO();
        dto.setAlgorithm(ALGORITHM);
        dto.setMode(mode);
        dto.setQueueLength(items.size());
        dto.setAvailablePileCount((int) availablePileCount);
        dto.setTotalOpenQueueSlots(totalOpenSlots);
        dto.setRemainingQueueCapacity(Math.max(0, totalOpenSlots - items.size()));
        dto.setEstimatedWaitTime(0);
        dto.setTemporarySimulationNote("Event-triggered dispatch: RECOVERY_QUEUE > MIGRATION_QUEUE > WAITING_AREA, then shortest estimated finish time.");
        dto.setWaitingList(items);
        return dto;
    }

    private Optional<ChargingRequest> nextWaitingRequest(ChargeMode mode) {
        Optional<ChargingRequest> recovery = firstQueueRequest(mode, QueueArea.RECOVERY_QUEUE);
        if (recovery.isPresent()) {
            return recovery;
        }
        Optional<ChargingRequest> migration = firstQueueRequest(mode, QueueArea.MIGRATION_QUEUE);
        if (migration.isPresent()) {
            return migration;
        }
        return firstQueueRequest(mode, QueueArea.WAITING_AREA);
    }

    private void triggerPriorityDispatch(ChargeMode mode) {
        while (true) {
            ChargingRequest next = nextPriorityRequest(mode).orElse(null);
            if (next == null) {
                return;
            }

            ChargingPile targetPile = bestCandidatePile(mode, next).orElse(null);
            if (targetPile == null) {
                return;
            }

            if (!moveToPileQueue(next, targetPile)) {
                return;
            }
        }
    }

    private Optional<ChargingRequest> nextPriorityRequest(ChargeMode mode) {
        Optional<ChargingRequest> recovery = firstQueueRequest(mode, QueueArea.RECOVERY_QUEUE);
        if (recovery.isPresent()) {
            return recovery;
        }
        return firstQueueRequest(mode, QueueArea.MIGRATION_QUEUE);
    }

    private Optional<ChargingRequest> firstQueueRequest(ChargeMode mode, QueueArea area) {
        return requestRepository
                .findByModeAndStatusAndQueueAreaOrderByCreatedAtAsc(mode, ChargingRequestStatus.WAITING, area)
                .stream()
                .min(queueOrderComparator());
    }

    private Optional<ChargingPile> bestCandidatePile(ChargeMode mode, ChargingRequest request) {
        return pileRepository.findAll().stream()
                .filter(pile -> pile.getMode() == mode)
                .filter(this::isServiceable)
                .filter(this::isOpenForQueue)
                .min(Comparator
                        .comparing((ChargingPile pile) -> estimatedFinishMinutes(pile, request))
                        .thenComparing(ChargingPile::getCode));
    }

    private Optional<ChargingPile> bestAvailablePile(ChargeMode mode, ChargingRequest request) {
        return pileRepository.findAll().stream()
                .filter(pile -> pile.getMode() == mode)
                .filter(this::isServiceable)
                .filter(this::isOpenForQueue)
                .filter(pile -> waitingQueueSize(pile.getId()) < pileQueueCapacity())
                .min(Comparator
                        .comparing((ChargingPile pile) -> estimatedFinishMinutes(pile, request))
                        .thenComparing(ChargingPile::getCode));
    }

    private void assignToBestAvailablePileQueue(ChargingRequest request) {
        ChargingPile pile = bestAvailablePile(request.getMode(), request).orElse(null);
        if (pile != null) {
            moveToPileQueue(request, pile);
        }
    }

    private boolean moveToPileQueue(ChargingRequest request, ChargingPile pile) {
        boolean canReserveNow = pile.getStatus() == ChargingPileStatus.IDLE && firstPileQueueRequest(pile.getId()).isEmpty();
        if (!canReserveNow && waitingQueueSize(pile.getId()) >= pileQueueCapacity()) {
            return false;
        }
        request.setAssignedPileId(pile.getId());
        request.setQueueArea(QueueArea.PILE_QUEUE);
        request.setQueueNumber(canReserveNow ? null : waitingQueueSize(pile.getId()) + 1);
        request.setStatus(canReserveNow ? ChargingRequestStatus.ASSIGNED : ChargingRequestStatus.WAITING);
        if (canReserveNow) {
            request.setAssignedAt(LocalDateTime.now());
            pile.setStatus(ChargingPileStatus.RESERVED);
            pileRepository.save(pile);
        } else {
            request.setAssignedAt(null);
        }
        requestRepository.save(request);
        renumberPileQueue(pile.getId());
        return true;
    }

    private void reserveIdlePileHeads(ChargeMode mode, List<DispatchResult> assigned) {
        List<ChargingPile> idlePiles = pileRepository.findByModeAndStatusAndEnabledTrue(mode, ChargingPileStatus.IDLE)
                .stream()
                .filter(this::isOpenForQueue)
                .sorted(Comparator.comparing(ChargingPile::getCode))
                .toList();
        for (ChargingPile pile : idlePiles) {
            firstPileQueueRequest(pile.getId()).ifPresent(request -> {
                request.setStatus(ChargingRequestStatus.ASSIGNED);
                request.setAssignedAt(LocalDateTime.now());
                request.setQueueNumber(null);
                pile.setStatus(ChargingPileStatus.RESERVED);
                requestRepository.save(request);
                pileRepository.save(pile);
                assigned.add(toDispatchResult(request, pile));
                renumberPileQueue(pile.getId());
            });
        }
    }

    private boolean isServiceable(ChargingPile pile) {
        return Boolean.TRUE.equals(pile.getEnabled())
                && pile.getStatus() != ChargingPileStatus.FAULT
                && pile.getStatus() != ChargingPileStatus.OFFLINE;
    }

    private Optional<ChargingRequest> firstPileQueueRequest(Long pileId) {
        return pileQueueRequests(pileId).stream()
                .min(queueOrderComparator());
    }

    private List<ChargingRequest> pileQueueRequests(Long pileId) {
        return requestRepository.findByAssignedPileIdAndStatusInOrderByQueueNumberAscCreatedAtAsc(
                        pileId, PILE_QUEUE_STATUSES)
                .stream()
                .filter(request -> request.getQueueArea() == QueueArea.PILE_QUEUE)
                .toList();
    }

    private int waitingQueueSize(Long pileId) {
        return (int) pileQueueRequests(pileId).stream()
                .filter(request -> request.getStatus() == ChargingRequestStatus.WAITING)
                .count();
    }

    private void markAsWaitingArea(ChargingRequest request) {
        request.setQueueArea(QueueArea.WAITING_AREA);
        request.setAssignedPileId(null);
        request.setAssignedAt(null);
        request.setQueueNumber(null);
        request.setStatus(ChargingRequestStatus.WAITING);
        requestRepository.save(request);
    }

    private void releaseOrphanReservedPiles(ChargeMode mode) {
        for (ChargingPile pile : pileRepository.findByModeAndEnabledTrue(mode)) {
            if (pile.getStatus() != ChargingPileStatus.RESERVED) {
                continue;
            }
            boolean hasAssigned = pileQueueRequests(pile.getId()).stream()
                    .anyMatch(request -> request.getStatus() == ChargingRequestStatus.ASSIGNED);
            if (!hasAssigned) {
                pile.setStatus(ChargingPileStatus.IDLE);
                pileRepository.save(pile);
            }
        }
    }

    private void renumberPileQueue(Long pileId) {
        List<ChargingRequest> queue = pileQueueRequests(pileId).stream()
                .filter(request -> request.getStatus() == ChargingRequestStatus.WAITING)
                .sorted(queueOrderComparator())
                .toList();
        int number = 1;
        for (ChargingRequest request : queue) {
            request.setQueueNumber(number++);
            requestRepository.save(request);
        }
    }

    private long estimatedFinishMinutes(ChargingPile pile, ChargingRequest request) {
        return expectedWaitMinutes(pile) + estimatedDurationMinutes(request, pile);
    }

    private long expectedWaitMinutes(ChargingPile pile) {
        long activeMinutes = sessionRepository.findByPileIdAndStatus(pile.getId(), ChargingSessionStatus.CHARGING)
                .stream()
                .findFirst()
                .map(session -> estimatedSessionRemainingMinutes(session, pile))
                .orElse(0L);
        long queuedMinutes = pileQueueRequests(pile.getId()).stream()
                .mapToLong(request -> estimatedDurationMinutes(request, pile))
                .sum();
        return activeMinutes + queuedMinutes;
    }

    private long estimatedSessionRemainingMinutes(ChargingSession session, ChargingPile pile) {
        BigDecimal remaining = safe(session.getTargetAmount()).subtract(safe(session.getChargedAmount()))
                .max(BigDecimal.ZERO);
        if (safe(pile.getPower()).compareTo(BigDecimal.ZERO) <= 0) {
            return Long.MAX_VALUE;
        }
        return remaining.multiply(BigDecimal.valueOf(60))
                .divide(pile.getPower(), 0, RoundingMode.CEILING)
                .longValue();
    }

    private Comparator<ChargingRequest> queueViewComparator() {
        return Comparator
                .comparing((ChargingRequest request) -> queueAreaRank(request.getQueueArea()))
                .thenComparing(ChargingRequest::getAssignedPileId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChargingRequest::getQueueNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChargingRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChargingRequest::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<ChargingRequest> queueOrderComparator() {
        return Comparator
                .comparing(ChargingRequest::getQueueNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChargingRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ChargingRequest::getId, Comparator.nullsLast(Comparator.naturalOrder()));
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
        dto.setQueueArea(request.getQueueArea());
        dto.setRecoveryPriority(request.getQueueArea() == QueueArea.RECOVERY_QUEUE);
        dto.setStatus(request.getStatus());
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }

    private DispatchResult toDispatchResult(ChargingRequest request, ChargingPile pile) {
        DispatchResult result = new DispatchResult();
        result.setAlgorithm(ALGORITHM);
        result.setRequestId(request.getId());
        result.setPileId(pile.getId());
        result.setPileCode(pile.getCode());
        result.setMode(pile.getMode());
        result.setRemainingAmount(remainingAmount(request));
        result.setEstimatedDurationMinutes(estimatedDurationMinutes(request, pile));
        result.setRequestStatus(request.getStatus());
        result.setPileStatus(pile.getStatus());
        result.setEstimatedStartTime(LocalDateTime.now());
        return result;
    }

    private int pileQueueCapacity() {
        return Math.max(0, chargingProperties.getQueue().getPileCapacity());
    }

    private int queueAreaRank(QueueArea area) {
        if (area == QueueArea.RECOVERY_QUEUE) {
            return 0;
        }
        if (area == QueueArea.MIGRATION_QUEUE) {
            return 1;
        }
        if (area == QueueArea.PILE_QUEUE) {
            return 2;
        }
        if (area == QueueArea.WAITING_AREA) {
            return 3;
        }
        return 4;
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

    public int totalOpenQueueSlots(ChargeMode mode) {
        return pileRepository.findByModeAndEnabledTrue(mode).stream()
                .mapToInt(p -> Math.max(0, p.getOpenQueueSlots() == null ? 0 : p.getOpenQueueSlots()))
                .sum();
    }

    private boolean isOpenForQueue(ChargingPile pile) {
        return Boolean.TRUE.equals(pile.getEnabled())
                && pile.getOpenQueueSlots() != null
                && pile.getOpenQueueSlots() > 0;
    }
}
