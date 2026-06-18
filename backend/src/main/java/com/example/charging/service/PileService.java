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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PileService {

    private static final String TEMP_FAULT_NOTE = "当前项目尚无 fault_report/maintenance_record 表；故障原因、恢复记录与硬件电表读数为接口层临时模拟信息。";
    private static final String TEMP_PRIORITY_NOTE = "断点续充优先级临时复用 queueNumber=0 表示；等待正式队列/优先级字段后可替换。";

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
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));

        PileFaultResult result = new PileFaultResult();
        if (pile.getStatus() == ChargingPileStatus.CHARGING) {
            interruptActiveSession(pile, chargedAmount, result);
        }

        pile.setStatus(ChargingPileStatus.FAULT);
        pileRepository.save(pile);

        result.setPileId(pile.getId());
        result.setStatus(pile.getStatus());
        result.setFaultReason(faultReason);
        result.setTemporarySimulation(true);
        result.setTemporarySimulationNote(TEMP_FAULT_NOTE + " " + TEMP_PRIORITY_NOTE);
        return result;
    }

    @Transactional
    public PileRecoverResult recover(Long pileId) {
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));
        if (pile.getStatus() != ChargingPileStatus.FAULT) {
            throw new IllegalArgumentException("只有 FAULT 状态的充电桩可以恢复");
        }

        pile.setStatus(ChargingPileStatus.IDLE);
        pileRepository.save(pile);

        PileRecoverResult result = new PileRecoverResult();
        result.setPileId(pile.getId());
        result.setStatus(pile.getStatus());
        result.setTemporarySimulation(true);
        result.setTemporarySimulationNote(TEMP_FAULT_NOTE + " 恢复后会自动尝试使用 SJF 调度同类型等待请求。");
        boolean hasWaitingRequest = !requestRepository
                .findByModeAndStatusOrderByCreatedAtAsc(pile.getMode(), ChargingRequestStatus.WAITING)
                .isEmpty();
        if (hasWaitingRequest) {
            result.setDispatchResult(schedulerService.dispatch(pile.getMode()));
        } else {
            result.setDispatchResult(null);
        }
        return result;
    }

    private void interruptActiveSession(ChargingPile pile,
                                        BigDecimal chargedAmount,
                                        PileFaultResult result) {
        ChargingSession session = sessionRepository
                .findByPileIdAndStatus(pile.getId(), ChargingSessionStatus.CHARGING)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("充电桩状态为 CHARGING，但未找到进行中的会话"));
        ChargingRequest request = requestRepository.findById(session.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("充电请求不存在"));

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
