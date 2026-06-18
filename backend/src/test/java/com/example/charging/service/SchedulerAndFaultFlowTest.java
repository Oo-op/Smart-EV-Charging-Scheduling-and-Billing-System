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
import com.example.charging.enums.QueueArea;
import com.example.charging.repository.ChargingPileRepository;
import com.example.charging.repository.ChargingRequestRepository;
import com.example.charging.repository.ChargingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void clearData() {
        sessionRepository.deleteAll();
        requestRepository.deleteAll();
        pileRepository.deleteAll();
    }

    @Test
    void dispatchTakesWaitingAreaFifoThenAssignsPile() {
        ChargingPile pile = savePile("T-F01", ChargeMode.FAST, "60.00");
        ChargingRequest firstRequest = saveRequest(1L, 1L, ChargeMode.FAST, "30.00");
        ChargingRequest shortRequest = saveRequest(2L, 2L, ChargeMode.FAST, "10.00");

        DispatchResult result = schedulerService.dispatch(ChargeMode.FAST);

        assertEquals("SJF", result.getAlgorithm());
        assertEquals(firstRequest.getId(), result.getRequestId());
        assertEquals(pile.getId(), result.getPileId());
        assertEquals(new BigDecimal("30.00"), result.getRemainingAmount());
        assertEquals(ChargingRequestStatus.WAITING, requestRepository.findById(shortRequest.getId()).orElseThrow().getStatus());
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

    @Test
    void ordinaryDispatchSkipsFullPilesAndChoosesShortestFinishAmongCandidates() {
        ChargingPile fullPile = savePile("T-S01", ChargeMode.SLOW, "7.00");
        ChargingPile secondPile = savePile("T-S02", ChargeMode.SLOW, "7.00");
        fullPile.setStatus(ChargingPileStatus.CHARGING);
        secondPile.setStatus(ChargingPileStatus.CHARGING);
        pileRepository.save(fullPile);
        pileRepository.save(secondPile);
        saveQueuedRequest(10L, 10L, ChargeMode.SLOW, "1.00", fullPile.getId(), 1);
        saveQueuedRequest(11L, 11L, ChargeMode.SLOW, "1.00", fullPile.getId(), 2);
        saveQueuedRequest(12L, 12L, ChargeMode.SLOW, "30.00", secondPile.getId(), 1);

        ChargingRequest incoming = saveRequest(13L, 13L, ChargeMode.SLOW, "5.00");

        schedulerService.enqueueNewRequest(incoming.getId());

        ChargingRequest saved = requestRepository.findById(incoming.getId()).orElseThrow();
        assertEquals(QueueArea.PILE_QUEUE, saved.getQueueArea());
        assertEquals(secondPile.getId(), saved.getAssignedPileId());
    }

    @Test
    void faultMigrationDoesNotPullOrdinaryWaitingAreaRequests() {
        ChargingPile faultedPile = savePile("T-S03", ChargeMode.SLOW, "7.00");
        ChargingPile serviceablePile = savePile("T-S04", ChargeMode.SLOW, "7.00");
        ChargingRequest migrationRequest = saveQueuedRequest(20L, 20L, ChargeMode.SLOW, "6.00",
                faultedPile.getId(), 1);
        ChargingRequest waitingRequest = saveRequest(21L, 21L, ChargeMode.SLOW, "5.00");
        ChargingRequest nextWaitingRequest = saveRequest(22L, 22L, ChargeMode.SLOW, "5.00");

        pileService.markFault(faultedPile.getId(), "test fault", BigDecimal.ZERO);
        schedulerService.triggerPriorityDispatch(ChargeMode.SLOW);

        ChargingRequest migrated = requestRepository.findById(migrationRequest.getId()).orElseThrow();
        ChargingRequest waiting = requestRepository.findById(waitingRequest.getId()).orElseThrow();
        ChargingRequest nextWaiting = requestRepository.findById(nextWaitingRequest.getId()).orElseThrow();
        assertEquals(serviceablePile.getId(), migrated.getAssignedPileId());
        assertEquals(QueueArea.PILE_QUEUE, migrated.getQueueArea());
        assertEquals(QueueArea.WAITING_AREA, waiting.getQueueArea());
        assertNull(waiting.getAssignedPileId());
        assertEquals(QueueArea.WAITING_AREA, nextWaiting.getQueueArea());
        assertNull(nextWaiting.getAssignedPileId());
    }

    @Test
    void startingSessionDoesNotPullOrdinaryWaitingAreaRequests() {
        ChargingPile pile = savePile("T-S05", ChargeMode.SLOW, "7.00");
        pile.setStatus(ChargingPileStatus.RESERVED);
        pileRepository.save(pile);
        ChargingRequest assigned = saveQueuedRequest(30L, 30L, ChargeMode.SLOW, "6.00", pile.getId(), 1);
        assigned.setStatus(ChargingRequestStatus.ASSIGNED);
        requestRepository.save(assigned);
        ChargingRequest waiting = saveRequest(31L, 31L, ChargeMode.SLOW, "5.00");

        sessionService.start(assigned.getId(), pile.getId());

        ChargingRequest savedWaiting = requestRepository.findById(waiting.getId()).orElseThrow();
        assertEquals(QueueArea.WAITING_AREA, savedWaiting.getQueueArea());
        assertNull(savedWaiting.getAssignedPileId());
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
        request.setQueueArea(QueueArea.WAITING_AREA);
        request.setQueueNumber(1);
        return requestRepository.save(request);
    }

    private ChargingRequest saveQueuedRequest(Long userId, Long vehicleId, ChargeMode mode,
                                              String targetAmount, Long pileId, int queueNumber) {
        ChargingRequest request = saveRequest(userId, vehicleId, mode, targetAmount);
        request.setAssignedPileId(pileId);
        request.setQueueArea(QueueArea.PILE_QUEUE);
        request.setQueueNumber(queueNumber);
        return requestRepository.save(request);
    }
}
