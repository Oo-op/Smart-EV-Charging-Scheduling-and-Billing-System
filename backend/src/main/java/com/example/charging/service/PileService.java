package com.example.charging.service;

import com.example.charging.dto.AdminPileCapacityUpdateRequest;
import com.example.charging.dto.PileDTO;
import com.example.charging.dto.PileFaultResult;
import com.example.charging.dto.PileRecoverResult;
import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ChargingRequest;
import com.example.charging.entity.ChargingSession;
import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.enums.ChargingSessionStatus;
import com.example.charging.repository.ChargingPileRepository;
import com.example.charging.repository.ChargingRequestRepository;
import com.example.charging.repository.ChargingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PileService {

    private final ChargingPileRepository pileRepository;
    private final ChargingSessionRepository sessionRepository;
    private final ChargingRequestRepository requestRepository;
    private final SchedulerService schedulerService;

    public PileService(ChargingPileRepository pileRepository,
                       ChargingSessionRepository sessionRepository,
                       ChargingRequestRepository requestRepository,
                       SchedulerService schedulerService) {
        this.pileRepository = pileRepository;
        this.sessionRepository = sessionRepository;
        this.requestRepository = requestRepository;
        this.schedulerService = schedulerService;
    }

    public List<PileDTO> listPiles() {
        return pileRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PileDTO updateCapacity(Long pileId, AdminPileCapacityUpdateRequest request) {
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));

        if (request.getEnabled() != null && !request.getEnabled()
                && (pile.getStatus() == ChargingPileStatus.CHARGING || pile.getStatus() == ChargingPileStatus.RESERVED)) {
            throw new IllegalArgumentException("充电中或已分配中的充电桩不能直接关闭");
        }

        if (request.getEnabled() != null) {
            pile.setEnabled(request.getEnabled());
            if (request.getEnabled()) {
                if (pile.getStatus() == ChargingPileStatus.OFFLINE) {
                    pile.setStatus(ChargingPileStatus.IDLE);
                }
                if (safeInt(pile.getOpenQueueSlots(), 0) == 0) {
                    pile.setOpenQueueSlots(1);
                }
            } else {
                pile.setOpenQueueSlots(0);
                if (pile.getStatus() == ChargingPileStatus.IDLE) {
                    pile.setStatus(ChargingPileStatus.OFFLINE);
                }
            }
        }

        if (request.getOpenQueueSlots() != null) {
            int maxSlots = safeInt(pile.getMaxQueueSlots(), 0);
            int openSlots = request.getOpenQueueSlots();
            if (openSlots < 0 || openSlots > maxSlots) {
                throw new IllegalArgumentException("开放位置数量必须在 0 到固定上限之间");
            }
            pile.setOpenQueueSlots(openSlots);
            pile.setEnabled(openSlots > 0);
            if (openSlots > 0 && pile.getStatus() == ChargingPileStatus.OFFLINE) {
                pile.setStatus(ChargingPileStatus.IDLE);
            }
            if (openSlots == 0 && pile.getStatus() == ChargingPileStatus.IDLE) {
                pile.setStatus(ChargingPileStatus.OFFLINE);
            }
        }

        return toDto(pileRepository.save(pile));
    }

    @Transactional
    public PileFaultResult markFault(Long pileId, String faultReason, BigDecimal chargedAmount) {
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new IllegalArgumentException("Charging pile not found"));

        PileFaultResult result = new PileFaultResult();
        if (pile.getStatus() == ChargingPileStatus.CHARGING) {
            interruptActiveSession(pile, chargedAmount, result);
        }

        pile.setStatus(ChargingPileStatus.FAULT);
        pileRepository.save(pile);

        if (result.getRecoveredRequestId() != null && result.getRequestStatus() == ChargingRequestStatus.WAITING) {
            schedulerService.placeRecoveryRequest(result.getRecoveredRequestId());
        }
        schedulerService.migrateFaultedPileQueue(pile.getId());

        result.setPileId(pile.getId());
        result.setStatus(pile.getStatus());
        result.setFaultReason(faultReason);
        result.setTemporarySimulation(false);
        result.setTemporarySimulationNote("Fault handling migrated active recovery request first, then original pile queue before waiting area.");
        return result;
    }

    @Transactional
    public PileRecoverResult recover(Long pileId) {
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new IllegalArgumentException("Charging pile not found"));
        if (pile.getStatus() != ChargingPileStatus.FAULT) {
            throw new IllegalArgumentException("Only FAULT piles can recover");
        }

        pile.setStatus(ChargingPileStatus.IDLE);
        pileRepository.save(pile);

        PileRecoverResult result = new PileRecoverResult();
        result.setPileId(pile.getId());
        result.setStatus(pile.getStatus());
        result.setTemporarySimulation(false);
        result.setTemporarySimulationNote("Recovered pile promotes its own queue, then recovery/migration queues, then waiting area.");
        result.setDispatchResult(schedulerService.promoteNextForPile(pile.getId()));
        return result;
    }

    private void interruptActiveSession(ChargingPile pile,
                                        BigDecimal chargedAmount,
                                        PileFaultResult result) {
        ChargingSession session = sessionRepository
                .findByPileIdAndStatus(pile.getId(), ChargingSessionStatus.CHARGING)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pile is CHARGING but no active session was found"));
        ChargingRequest request = requestRepository.findById(session.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("Charging request not found"));

        BigDecimal sessionCharged = chargedAmount == null ? safe(session.getChargedAmount()) : safe(chargedAmount);
        BigDecimal totalCharged = safe(request.getChargedAmount()).add(sessionCharged);
        BigDecimal remaining = safe(request.getTargetAmount()).subtract(totalCharged)
                .max(BigDecimal.ZERO);

        session.setChargedAmount(sessionCharged);
        session.setEndTime(LocalDateTime.now());
        session.setStatus(ChargingSessionStatus.INTERRUPTED);

        request.setChargedAmount(totalCharged);
        request.setAssignedPileId(null);
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            request.setStatus(ChargingRequestStatus.WAITING);
            request.setQueueNumber(0);
        } else {
            request.setStatus(ChargingRequestStatus.COMPLETED);
        }

        sessionRepository.save(session);
        requestRepository.save(request);

        result.setInterruptedSessionId(session.getId());
        result.setSessionStatus(session.getStatus());
        result.setRecoveredRequestId(request.getId());
        result.setRequestStatus(request.getStatus());
        result.setChargedAmount(totalCharged);
        result.setRemainingAmount(remaining);
    }

    private PileDTO toDto(ChargingPile pile) {
        PileDTO dto = new PileDTO();
        dto.setPileId(pile.getId());
        dto.setCode(pile.getCode());
        dto.setMode(pile.getMode());
        dto.setPower(pile.getPower());
        dto.setStatus(pile.getStatus());
        dto.setServiceFee(pile.getServiceFee());
        dto.setEnabled(Boolean.TRUE.equals(pile.getEnabled()));
        dto.setOpenQueueSlots(safeInt(pile.getOpenQueueSlots(), 0));
        dto.setMaxQueueSlots(safeInt(pile.getMaxQueueSlots(), 0));
        return dto;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int safeInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
