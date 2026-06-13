package com.example.charging.service;

import com.example.charging.dto.BillSummaryDTO;
import com.example.charging.dto.SessionDTO;
import com.example.charging.dto.StopSessionResult;
import com.example.charging.entity.Bill;
import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ChargingRequest;
import com.example.charging.entity.ChargingSession;
import com.example.charging.enums.BillStatus;
import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.enums.ChargingSessionStatus;
import com.example.charging.repository.BillRepository;
import com.example.charging.repository.ChargingPileRepository;
import com.example.charging.repository.ChargingRequestRepository;
import com.example.charging.repository.ChargingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class SessionService {

    private static final BigDecimal DEFAULT_ELECTRICITY_FEE = new BigDecimal("0.80");

    private final ChargingRequestRepository requestRepository;
    private final ChargingPileRepository pileRepository;
    private final ChargingSessionRepository sessionRepository;
    private final BillRepository billRepository;

    public SessionService(ChargingRequestRepository requestRepository,
                          ChargingPileRepository pileRepository,
                          ChargingSessionRepository sessionRepository,
                          BillRepository billRepository) {
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.sessionRepository = sessionRepository;
        this.billRepository = billRepository;
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
        ChargingSession session = new ChargingSession();
        session.setRequestId(request.getId());
        session.setUserId(request.getUserId());
        session.setVehicleId(request.getVehicleId());
        session.setPileId(pile.getId());
        session.setStartTime(now);
        session.setTargetAmount(remainingAmount(request));
        session.setChargedAmount(BigDecimal.ZERO);
        session.setStatus(ChargingSessionStatus.CHARGING);

        request.setStatus(ChargingRequestStatus.CHARGING);
        pile.setStatus(ChargingPileStatus.CHARGING);

        ChargingSession saved = sessionRepository.save(session);
        requestRepository.save(request);
        pileRepository.save(pile);
        return toSessionDto(saved, request, pile);
    }

    @Transactional
    public StopSessionResult stop(Long sessionId, BigDecimal chargedAmount) {
        ChargingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("充电会话不存在"));
        if (session.getStatus() != ChargingSessionStatus.CHARGING) {
            throw new IllegalArgumentException("充电会话必须处于 CHARGING 状态");
        }

        ChargingRequest request = requestRepository.findById(session.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("充电请求不存在"));
        ChargingPile pile = pileRepository.findById(session.getPileId())
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));

        BigDecimal actualAmount = chargedAmount == null ? session.getTargetAmount() : chargedAmount;
        actualAmount = safe(actualAmount).setScale(2, RoundingMode.HALF_UP);
        LocalDateTime now = LocalDateTime.now();

        session.setChargedAmount(actualAmount);
        session.setEndTime(now);
        session.setStatus(ChargingSessionStatus.COMPLETED);
        request.setChargedAmount(actualAmount);
        request.setStatus(ChargingRequestStatus.COMPLETED);
        pile.setStatus(ChargingPileStatus.IDLE);

        ChargingSession savedSession = sessionRepository.save(session);
        requestRepository.save(request);
        pileRepository.save(pile);
        Bill bill = billRepository.save(createBill(savedSession, pile, actualAmount));
        return toStopResult(savedSession, bill);
    }

    private Bill createBill(ChargingSession session, ChargingPile pile, BigDecimal chargedAmount) {
        BigDecimal electricityFee = chargedAmount.multiply(DEFAULT_ELECTRICITY_FEE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceFee = chargedAmount.multiply(safe(pile.getServiceFee()))
                .setScale(2, RoundingMode.HALF_UP);

        Bill bill = new Bill();
        bill.setUserId(session.getUserId());
        bill.setSessionId(session.getId());
        bill.setElectricityFee(electricityFee);
        bill.setServiceFee(serviceFee);
        bill.setTotalFee(electricityFee.add(serviceFee));
        bill.setStatus(BillStatus.UNPAID);
        return bill;
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

    private BigDecimal remainingAmount(ChargingRequest request) {
        BigDecimal remaining = safe(request.getTargetAmount()).subtract(safe(request.getChargedAmount()));
        return remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
