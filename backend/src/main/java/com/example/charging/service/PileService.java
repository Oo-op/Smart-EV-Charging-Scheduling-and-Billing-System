package com.example.charging.service;

import com.example.charging.dto.AdminPileCapacityUpdateRequest;
import com.example.charging.dto.DispatchResult;
import com.example.charging.dto.PileCapacityUpdateResult;
import com.example.charging.dto.PileDTO;
import com.example.charging.dto.PileFaultResult;
import com.example.charging.dto.PileRecoverResult;
import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ChargingRequest;
import com.example.charging.entity.ChargingSession;
import com.example.charging.enums.ChargeMode;
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
import java.util.ArrayList;
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
    public PileCapacityUpdateResult updateCapacity(Long pileId, AdminPileCapacityUpdateRequest request) {
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));

        if (request.getEnabled() != null && !request.getEnabled()
                && (pile.getStatus() == ChargingPileStatus.CHARGING || pile.getStatus() == ChargingPileStatus.RESERVED)) {
            throw new IllegalArgumentException("充电中或已分配中的充电桩不能直接关闭");
        }

        boolean wasDispatchEligible = isDispatchEligible(pile);
        int previousOpenSlots = safeInt(pile.getOpenQueueSlots(), 0);

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

        ChargingPile saved = pileRepository.save(pile);
        boolean nowDispatchEligible = isDispatchEligible(saved);
        boolean slotsIncreased = safeInt(saved.getOpenQueueSlots(), 0) > previousOpenSlots;

        PileCapacityUpdateResult result = new PileCapacityUpdateResult();
        result.setPile(toDto(saved));

        if (nowDispatchEligible && (!wasDispatchEligible || slotsIncreased)) {
            List<DispatchResult> dispatchResults = schedulerService.redispatchWaitingArea(saved.getMode());
            result.setDispatchTriggered(true);
            result.setAssignedCount(dispatchResults.size());
            result.setDispatchResults(dispatchResults);
            result.setNote("开放桩位后已对等候区全部车辆重新调度（SJF）");
        } else {
            result.setDispatchTriggered(false);
            result.setAssignedCount(0);
            result.setDispatchResults(new ArrayList<>());
            result.setNote("未触发调度：桩位未变为可分配空闲状态，或仅缩小开放容量");
        }
        return result;
    }

    /** 空闲、已开放且有空位的桩才参与「开桩消化等候区」调度 */
    private boolean isDispatchEligible(ChargingPile pile) {
        return Boolean.TRUE.equals(pile.getEnabled())
                && safeInt(pile.getOpenQueueSlots(), 0) > 0
                && pile.getStatus() == ChargingPileStatus.IDLE;
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

        result.setPileId(pile.getId());
        result.setStatus(pile.getStatus());
        result.setFaultReason(faultReason);
        result.setTemporarySimulation(false);
        result.setTemporarySimulationNote("Fault handling will be migrated during the next scheduling step.");
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
        if (pile.getMode() == ChargeMode.FAST) {
            schedulerService.restoreRecoveredPilePriorityOrder(pile.getId());
        }

        PileRecoverResult result = new PileRecoverResult();
        result.setPileId(pile.getId());
        result.setStatus(pile.getStatus());
        result.setTemporarySimulation(false);
        result.setTemporarySimulationNote("Recovered pile promotes its own queue, then recovery/migration queues, then waiting area.");
        result.setDispatchResult(schedulerService.promoteNextForRecoveredPile(pile.getId()));
        schedulerService.resumeWaitingAreaAfterAllFaultsRecovered();
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
            request.setPriorityDispatch(true);
        } else {
            request.setStatus(ChargingRequestStatus.COMPLETED);
            request.setPriorityDispatch(false);
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
