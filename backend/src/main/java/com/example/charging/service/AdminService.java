package com.example.charging.service;

import com.example.charging.dto.*;
import com.example.charging.entity.ChargingPile;
import com.example.charging.entity.ChargingSession;
import com.example.charging.entity.Bill;
import com.example.charging.enums.BillStatus;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingPileStatus;
import com.example.charging.enums.ChargingSessionStatus;
import com.example.charging.repository.BillRepository;
import com.example.charging.repository.ChargingPileRepository;
import com.example.charging.repository.ChargingRequestRepository;
import com.example.charging.repository.ChargingSessionRepository;
import com.example.charging.repository.UserRepository;
import com.example.charging.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargingRequestRepository requestRepository;
    private final ChargingSessionRepository sessionRepository;
    private final ChargingPileRepository pileRepository;
    private final BillRepository billRepository;
    private final SchedulerService schedulerService;

    public AdminService(UserRepository userRepository,
                        VehicleRepository vehicleRepository,
                        ChargingRequestRepository requestRepository,
                        ChargingSessionRepository sessionRepository,
                        ChargingPileRepository pileRepository,
                        BillRepository billRepository,
                        SchedulerService schedulerService) {
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.requestRepository = requestRepository;
        this.sessionRepository = sessionRepository;
        this.pileRepository = pileRepository;
        this.billRepository = billRepository;
        this.schedulerService = schedulerService;
    }

    public AdminDashboardDTO getDashboard() {
        PileSummaryDTO pileSummary = new PileSummaryDTO();
        pileSummary.setIdle(pileRepository.countByStatus(ChargingPileStatus.IDLE));
        pileSummary.setCharging(
                pileRepository.countByStatus(ChargingPileStatus.CHARGING)
                        + pileRepository.countByStatus(ChargingPileStatus.RESERVED));
        pileSummary.setFault(pileRepository.countByStatus(ChargingPileStatus.FAULT));
        pileSummary.setOffline(pileRepository.countByStatus(ChargingPileStatus.OFFLINE));

        AdminDashboardDTO dto = new AdminDashboardDTO();
        dto.setTotalUsers(userRepository.count());
        dto.setTotalVehicles(vehicleRepository.count());
        dto.setTotalRequests(requestRepository.count());
        dto.setActiveSessions(sessionRepository.countByStatus(ChargingSessionStatus.CHARGING));
        dto.setTodayRevenue(calculateTodayRevenue());
        dto.setPileSummary(pileSummary);
        dto.setCapacityOverview(buildCapacityOverview());
        return dto;
    }

    public List<AdminPileDTO> listPilesWithSessions() {
        return pileRepository.findAll().stream()
                .map(this::toAdminPile)
                .toList();
    }

    public AdminQueueDTO getQueueOverview() {
        AdminQueueDTO dto = new AdminQueueDTO();
        dto.setFastQueue(toAdminQueueMode(schedulerService.getQueue(ChargeMode.FAST)));
        dto.setSlowQueue(toAdminQueueMode(schedulerService.getQueue(ChargeMode.SLOW)));
        return dto;
    }

    private BigDecimal calculateTodayRevenue() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        return billRepository.findByStatusAndCreatedAtBetween(BillStatus.PAID, start, end).stream()
                .map(Bill::getTotalFee)
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(safe(b)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private AdminPileDTO toAdminPile(ChargingPile pile) {
        AdminPileDTO dto = new AdminPileDTO();
        dto.setPileId(pile.getId());
        dto.setCode(pile.getCode());
        dto.setMode(pile.getMode());
        dto.setPower(pile.getPower());
        dto.setStatus(pile.getStatus());
        dto.setEnabled(Boolean.TRUE.equals(pile.getEnabled()));
        dto.setOpenQueueSlots(pile.getOpenQueueSlots());
        dto.setMaxQueueSlots(pile.getMaxQueueSlots());
        if (pile.getStatus() == ChargingPileStatus.CHARGING) {
            dto.setCurrentSession(findActiveSession(pile.getId()));
        }
        return dto;
    }

    private AdminPileSessionDTO findActiveSession(Long pileId) {
        return sessionRepository.findByPileIdAndStatus(pileId, ChargingSessionStatus.CHARGING)
                .stream()
                .findFirst()
                .map(this::toSessionDto)
                .orElse(null);
    }

    private AdminPileSessionDTO toSessionDto(ChargingSession session) {
        AdminPileSessionDTO dto = new AdminPileSessionDTO();
        dto.setSessionId(session.getId());
        dto.setRequestId(session.getRequestId());
        dto.setChargedAmount(session.getChargedAmount());
        dto.setTargetAmount(session.getTargetAmount());
        dto.setStartTime(session.getStartTime());
        return dto;
    }

    private AdminQueueModeDTO toAdminQueueMode(QueueStatusDTO queue) {
        AdminQueueModeDTO dto = new AdminQueueModeDTO();
        dto.setMode(queue.getMode());
        dto.setQueueLength(queue.getQueueLength());
        dto.setAvailablePileCount(queue.getAvailablePileCount());
        dto.setTotalOpenQueueSlots(queue.getTotalOpenQueueSlots());
        dto.setRemainingQueueCapacity(queue.getRemainingQueueCapacity());
        dto.setEstimatedWaitTime(queue.getEstimatedWaitTime());
        dto.setTemporarySimulationNote(queue.getTemporarySimulationNote());
        dto.setWaitingList(queue.getWaitingList().stream()
                .map(this::toAdminQueueItem)
                .toList());
        return dto;
    }

    private AdminQueueItemDTO toAdminQueueItem(QueueItemDTO item) {
        AdminQueueItemDTO dto = new AdminQueueItemDTO();
        dto.setRequestId(item.getRequestId());
        dto.setPlateNumber(item.getPlateNumber());
        dto.setTargetAmount(item.getTargetAmount());
        dto.setQueueNumber(item.getQueueNumber());
        dto.setStatus(item.getStatus());
        return dto;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private AdminCapacityOverviewDTO buildCapacityOverview() {
        List<ChargingPile> piles = pileRepository.findAll();
        AdminCapacityOverviewDTO dto = new AdminCapacityOverviewDTO();
        dto.setTotalPiles(piles.size());
        dto.setEnabledPiles(piles.stream().filter(p -> Boolean.TRUE.equals(p.getEnabled())).count());
        dto.setFastEnabledPiles(piles.stream()
                .filter(p -> p.getMode() == ChargeMode.FAST && Boolean.TRUE.equals(p.getEnabled()))
                .count());
        dto.setSlowEnabledPiles(piles.stream()
                .filter(p -> p.getMode() == ChargeMode.SLOW && Boolean.TRUE.equals(p.getEnabled()))
                .count());
        dto.setTotalOpenQueueSlots(piles.stream()
                .mapToInt(p -> p.getOpenQueueSlots() == null ? 0 : p.getOpenQueueSlots())
                .sum());
        dto.setTotalMaxQueueSlots(piles.stream()
                .mapToInt(p -> p.getMaxQueueSlots() == null ? 0 : p.getMaxQueueSlots())
                .sum());
        return dto;
    }
}
