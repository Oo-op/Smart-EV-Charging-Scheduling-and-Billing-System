package com.example.charging.service;

import com.example.charging.config.ChargingProperties;
import com.example.charging.dto.BillSummaryDTO;
import com.example.charging.dto.SessionDTO;
import com.example.charging.dto.StopSessionRequest;
import com.example.charging.dto.StopSessionResult;
import com.example.charging.entity.Bill;
import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ChargingRequest;
import com.example.charging.entity.ChargingSession;
import com.example.charging.enums.BillStatus;
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
public class SessionService {

    private final ChargingRequestRepository requestRepository;
    private final ChargingPileRepository pileRepository;
    private final ChargingSessionRepository sessionRepository;
    private final BillService billService;
    private final SchedulerService schedulerService;
    private final ChargingProperties chargingProperties;

    public SessionService(ChargingRequestRepository requestRepository,
                          ChargingPileRepository pileRepository,
                          ChargingSessionRepository sessionRepository,
                          BillService billService,
                          SchedulerService schedulerService,
                          ChargingProperties chargingProperties) {
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.sessionRepository = sessionRepository;
        this.billService = billService;
        this.schedulerService = schedulerService;
        this.chargingProperties = chargingProperties;
    }

    public SessionDTO getActiveSession(Long requestId, Long userId) {
        ChargingRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found or access denied"));
        if (request.getStatus() != ChargingRequestStatus.CHARGING) {
            throw new IllegalArgumentException("当前请求不在充电中");
        }
        ChargingSession session = sessionRepository
                .findFirstByRequestIdAndStatus(requestId, ChargingSessionStatus.CHARGING)
                .orElseThrow(() -> new IllegalArgumentException("未找到进行中的充电会话"));
        ChargingPile pile = pileRepository.findById(session.getPileId())
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));
        return toSessionDto(session, request, pile);
    }

    @Transactional
    public int autoStartTimedOutAssignments() {
        int timeoutMinutes = chargingProperties.getAssignment().getTimeoutMinutes();
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<ChargingRequest> expired = requestRepository.findByStatusAndAssignedAtLessThanEqual(
                ChargingRequestStatus.ASSIGNED, threshold);
        int started = 0;
        for (ChargingRequest request : expired) {
            if (request.getAssignedPileId() == null) {
                continue;
            }
            try {
                start(request.getId(), request.getAssignedPileId());
                started++;
            } catch (RuntimeException ignored) {
                // 并发或状态变化时跳过，下一轮重试
            }
        }
        return started;
    }

    @Transactional
    public SessionDTO start(Long requestId, Long pileId) {
        ChargingRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("充电请求不存在"));
        ChargingPile pile = pileRepository.findById(pileId)
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));

        if (request.getStatus() != ChargingRequestStatus.ASSIGNED) {
            throw new IllegalArgumentException("充电请求必须处于 ASSIGNED 状态");
        }
        if (!pile.getId().equals(request.getAssignedPileId())) {
            throw new IllegalArgumentException("充电桩与已分配请求不匹配");
        }
        if (pile.getStatus() != ChargingPileStatus.RESERVED && pile.getStatus() != ChargingPileStatus.IDLE) {
            throw new IllegalArgumentException("充电桩必须处于 RESERVED 或 IDLE 状态");
        }
        if (sessionRepository.findFirstByRequestIdAndStatus(requestId, ChargingSessionStatus.CHARGING).isPresent()) {
            throw new IllegalArgumentException("该请求已有进行中的充电会话");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal initialChargedAmount = initialChargeCredit(request, pile);
        ChargingSession session = new ChargingSession();
        session.setRequestId(request.getId());
        session.setUserId(request.getUserId());
        session.setVehicleId(request.getVehicleId());
        session.setPileId(pile.getId());
        session.setStartTime(now);
        session.setTargetAmount(remainingAmount(request));
        session.setChargedAmount(initialChargedAmount);
        session.setStatus(ChargingSessionStatus.CHARGING);

        request.setStatus(ChargingRequestStatus.CHARGING);
        request.setAssignedAt(null);
        request.setQueueArea(null);
        request.setQueueNumber(null);
        request.setPriorityDispatch(false);
        request.setInitialChargeCredit(false);
        pile.setStatus(ChargingPileStatus.CHARGING);

        ChargingSession saved = sessionRepository.save(session);
        requestRepository.save(request);
        pileRepository.save(pile);
        schedulerService.onPileQueueSlotFreed(pile.getId());
        return toSessionDto(saved, request, pile);
    }

    /**
     * 结束充电会话并生成账单。
     * 支持通过 StopSessionRequest 传递时间模拟参数进行测试：
     * - mockStartTime: 如果非空，将直接覆盖会话的开始时间。
     * - mockChargingHours: 如果非空，将根据结束时间（当前时间）往前推指定的小时数，并设置为会话开始时间。
     * 注：此模拟逻辑可用于在简短的本地测试中，生成跨越不同峰、平、谷电价时段的演示账单。
     */
    @Transactional
    public StopSessionResult stop(Long sessionId, StopSessionRequest req) {
        ChargingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("充电会话不存在"));
        if (session.getStatus() != ChargingSessionStatus.CHARGING) {
            throw new IllegalArgumentException("充电会话必须处于 CHARGING 状态");
        }

        ChargingRequest request = requestRepository.findById(session.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("充电请求不存在"));
        ChargingPile pile = pileRepository.findById(session.getPileId())
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));

        BigDecimal actualAmount = (req == null || req.getChargedAmount() == null) ? session.getTargetAmount() : req.getChargedAmount();
        actualAmount = safe(actualAmount);
        LocalDateTime now = (req != null && req.getMockEndTime() != null) ? req.getMockEndTime() : LocalDateTime.now();

        // 模拟时间逻辑处理
        if (req != null) {
            if (req.getMockStartTime() != null) {
                // 情形一：手动指定充电开始时间
                session.setStartTime(req.getMockStartTime());
            } else if (req.getMockChargingHours() != null) {
                // 情形二：自动将充电开始时间往前推指定的整数小时
                session.setStartTime(now.minusHours(req.getMockChargingHours()));
            }
        }

        session.setChargedAmount(actualAmount);
        session.setEndTime(now);
        session.setStatus(ChargingSessionStatus.COMPLETED);
        request.setChargedAmount(safe(request.getChargedAmount()).add(actualAmount));
        request.setStatus(ChargingRequestStatus.COMPLETED);
        request.setQueueArea(null);
        request.setQueueNumber(null);
        request.setPriorityDispatch(false);
        pile.setStatus(ChargingPileStatus.IDLE);

        ChargingSession savedSession = sessionRepository.save(session);
        requestRepository.save(request);
        pileRepository.save(pile);

        // 调用 BillService 分时计费生成账单
        Bill bill = billService.generateBill(savedSession);
        schedulerService.promoteNextForCompletedSession(pile.getId());
        return toStopResult(savedSession, bill);
    }

    private SessionDTO toSessionDto(ChargingSession session, ChargingRequest request, ChargingPile pile) {
        SessionDTO dto = new SessionDTO();
        dto.setSessionId(session.getId());
        dto.setRequestId(session.getRequestId());
        dto.setPileId(session.getPileId());
        dto.setMode(request.getMode());
        dto.setTargetAmount(session.getTargetAmount());
        dto.setChargedAmount(session.getChargedAmount());
        dto.setStatus(session.getStatus());
        dto.setStartTime(session.getStartTime());
        dto.setEstimatedEndTime(estimateEndTime(session.getStartTime(), session.getTargetAmount(), pile.getPower()));
        return dto;
    }

    private StopSessionResult toStopResult(ChargingSession session, Bill bill) {
        BillSummaryDTO billDto = new BillSummaryDTO();
        billDto.setBillId(bill.getId());
        billDto.setElectricityFee(bill.getElectricityFee());
        billDto.setServiceFee(bill.getServiceFee());
        billDto.setTotalFee(bill.getTotalFee());
        billDto.setStatus(bill.getStatus());

        StopSessionResult result = new StopSessionResult();
        result.setSessionId(session.getId());
        result.setRequestId(session.getRequestId());
        result.setPileId(session.getPileId());
        result.setChargedAmount(session.getChargedAmount());
        result.setStatus(session.getStatus());
        result.setEndTime(session.getEndTime());
        result.setBill(billDto);
        return result;
    }

    private LocalDateTime estimateEndTime(LocalDateTime startTime, BigDecimal targetAmount, BigDecimal power) {
        if (startTime == null || safe(power).compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        long minutes = safe(targetAmount).multiply(BigDecimal.valueOf(60))
                .divide(power, 0, RoundingMode.CEILING)
                .longValue();
        return startTime.plusMinutes(minutes);
    }

    private BigDecimal initialChargeCredit(ChargingRequest request, ChargingPile pile) {
        if (!Boolean.TRUE.equals(request.getInitialChargeCredit())) {
            return BigDecimal.ZERO;
        }
        if (safe(pile.getPower()).compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return pile.getPower().divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal remainingAmount(ChargingRequest request) {
        BigDecimal remaining = safe(request.getTargetAmount()).subtract(safe(request.getChargedAmount()));
        return remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
