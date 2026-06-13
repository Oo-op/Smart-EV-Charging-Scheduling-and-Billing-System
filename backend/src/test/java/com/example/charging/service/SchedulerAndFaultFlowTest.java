package com.example.charging.service;

import com.example.charging.dto.DispatchResult;
import com.example.charging.dto.PileFaultResult;
import com.example.charging.dto.PileRecoverResult;
import com.example.charging.dto.SessionDTO;
import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ChargingRequest;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.enums.ChargingSessionStatus;
import com.example.charging.repository.ChargingPileRepository;
import com.example.charging.repository.ChargingRequestRepository;
import com.example.charging.repository.ChargingSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SchedulerAndFaultFlowTest {

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private PileService pileService;

    @Autowired
    private ChargingPileRepository pileRepository;

    @Autowired
    private ChargingRequestRepository requestRepository;

    @Autowired
    private ChargingSessionRepository sessionRepository;

    @Test
    void dispatchUsesSjfShortestRemainingAmount() {
        ChargingPile pile = savePile("T-F01", ChargeMode.FAST, "60.00");
        ChargingRequest longRequest = saveRequest(1L, 1L, ChargeMode.FAST, "30.00");
        ChargingRequest shortRequest = saveRequest(2L, 2L, ChargeMode.FAST, "10.00");

        DispatchResult result = schedulerService.dispatch(ChargeMode.FAST);

        assertEquals("SJF", result.getAlgorithm());
        assertEquals(shortRequest.getId(), result.getRequestId());
        assertEquals(pile.getId(), result.getPileId());
        assertEquals(new BigDecimal("10.00"), result.getRemainingAmount());
        assertEquals(ChargingRequestStatus.WAITING, requestRepository.findById(longRequest.getId()).orElseThrow().getStatus());
        assertEquals(ChargingPileStatus.RESERVED, pileRepository.findById(pile.getId()).orElseThrow().getStatus());
    }

    @Test
    void faultInterruptsSessionAndRecoveryDispatchesRemainingRequest() {
        ChargingPile pile = savePile("T-F02", ChargeMode.FAST, "60.00");
        ChargingRequest request = saveRequest(3L, 3L, ChargeMode.FAST, "12.00");
        DispatchResult dispatch = schedulerService.dispatch(ChargeMode.FAST);
        SessionDTO session = sessionService.start(dispatch.getRequestId(), dispatch.getPileId());

        PileFaultResult fault = pileService.markFault(pile.getId(), "test fault", new BigDecimal("5.00"));

        ChargingRequest interruptedRequest = requestRepository.findById(request.getId()).orElseThrow();
        assertEquals(ChargingPileStatus.FAULT, pileRepository.findById(pile.getId()).orElseThrow().getStatus());
        assertEquals(ChargingSessionStatus.INTERRUPTED,
                sessionRepository.findById(session.getSessionId()).orElseThrow().getStatus());
        assertEquals(ChargingRequestStatus.WAITING, interruptedRequest.getStatus());
        assertEquals(0, interruptedRequest.getQueueNumber());
        assertEquals(new BigDecimal("5.00"), fault.getChargedAmount());
        assertEquals(new BigDecimal("7.00"), fault.getRemainingAmount());

        PileRecoverResult recover = pileService.recover(pile.getId());

        assertNotNull(recover.getDispatchResult());
        assertEquals(request.getId(), recover.getDispatchResult().getRequestId());
        assertEquals(new BigDecimal("7.00"), recover.getDispatchResult().getRemainingAmount());
        assertEquals(ChargingRequestStatus.ASSIGNED, requestRepository.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(ChargingPileStatus.RESERVED, pileRepository.findById(pile.getId()).orElseThrow().getStatus());
    }

    private ChargingPile savePile(String code, ChargeMode mode, String power) {
        ChargingPile pile = new ChargingPile();
        pile.setStationId(1L);
        pile.setCode(code);
        pile.setMode(mode);
        pile.setPower(new BigDecimal(power));
        pile.setStatus(ChargingPileStatus.IDLE);
        pile.setServiceFee(new BigDecimal("0.70"));
        return pileRepository.save(pile);
    }

    private ChargingRequest saveRequest(Long userId, Long vehicleId, ChargeMode mode, String targetAmount) {
        ChargingRequest request = new ChargingRequest();
        request.setUserId(userId);
        request.setVehicleId(vehicleId);
        request.setMode(mode);
        request.setTargetAmount(new BigDecimal(targetAmount));
        request.setChargedAmount(BigDecimal.ZERO);
        request.setStatus(ChargingRequestStatus.WAITING);
        request.setQueueNumber(1);
        return requestRepository.save(request);
    }
}
