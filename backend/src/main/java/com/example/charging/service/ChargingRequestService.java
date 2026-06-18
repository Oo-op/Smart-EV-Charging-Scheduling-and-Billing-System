package com.example.charging.service;

import com.example.charging.dto.ChargingRequestDetailDTO;
import com.example.charging.dto.ChargingRequestModifyRequest;
import com.example.charging.dto.ChargingRequestSubmitRequest;
import com.example.charging.entity.ChargingRequest;
import com.example.charging.entity.User;
import com.example.charging.entity.Vehicle;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingRequestStatus;
import com.example.charging.enums.QueueArea;
import com.example.charging.repository.ChargingRequestRepository;
import com.example.charging.repository.UserRepository;
import com.example.charging.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChargingRequestService {

    private final ChargingRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final SchedulerService schedulerService;

    public ChargingRequestService(ChargingRequestRepository requestRepository,
                                  UserRepository userRepository,
                                  VehicleRepository vehicleRepository,
                                  SchedulerService schedulerService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.schedulerService = schedulerService;
    }

    @Transactional
    public ChargingRequestDetailDTO submitRequest(ChargingRequestSubmitRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        if (!vehicle.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("Vehicle does not belong to user");
        }

        int totalOpenQueueSlots = schedulerService.totalOpenQueueSlots(request.getMode());
        if (totalOpenQueueSlots <= 0) {
            throw new IllegalArgumentException("褰撳墠妯″紡鏆傛湭寮€鏀撅紝璇疯仈绯荤鐞嗗憳");
        }

        ChargingRequest req = new ChargingRequest();
        req.setUserId(user.getId());
        req.setVehicleId(vehicle.getId());
        req.setMode(request.getMode());
        req.setTargetAmount(request.getTargetAmount());
        req.setChargedAmount(BigDecimal.ZERO);
        req.setStatus(ChargingRequestStatus.WAITING);
        req.setQueueArea(QueueArea.WAITING_AREA);
        req.setQueueNumber(null);
        req.setAssignedPileId(null);

        ChargingRequest saved = requestRepository.save(req);
        schedulerService.triggerDispatch(saved.getMode());
        saved = requestRepository.findById(saved.getId()).orElseThrow();
        return toDetailDto(saved, vehicle.getPlateNumber());
    }

    public ChargingRequestDetailDTO getRequestDetail(Long requestId, Long userId) {
        ChargingRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found or access denied"));
        return toDetailDto(request, resolvePlateNumber(request.getVehicleId()));
    }

    public List<ChargingRequestDetailDTO> listByUserId(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<ChargingRequest> requests = requestRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Vehicle> vehicles = vehicleRepository.findAllById(
                        requests.stream().map(ChargingRequest::getVehicleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));
        return requests.stream()
                .map(req -> {
                    Vehicle vehicle = vehicles.get(req.getVehicleId());
                    return toDetailDto(req, vehicle == null ? null : vehicle.getPlateNumber());
                })
                .toList();
    }

    @Transactional
    public ChargingRequestDetailDTO cancelRequest(Long requestId, Long userId) {
        ChargingRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found or access denied"));
        if (request.getStatus() != ChargingRequestStatus.WAITING
                && request.getStatus() != ChargingRequestStatus.ASSIGNED) {
            throw new IllegalArgumentException("Only waiting or assigned requests can be cancelled");
        }

        ChargeMode mode = request.getMode();
        QueueArea previousArea = request.getQueueArea();
        request.setStatus(ChargingRequestStatus.CANCELLED);
        request.setAssignedPileId(null);
        request.setQueueArea(null);
        request.setQueueNumber(null);
        ChargingRequest saved = requestRepository.save(request);
        if (previousArea == QueueArea.PILE_QUEUE) {
            schedulerService.triggerDispatch(mode);
        }
        return toDetailDto(saved, resolvePlateNumber(saved.getVehicleId()));
    }

    @Transactional
    public ChargingRequestDetailDTO modifyRequest(Long requestId, Long userId, ChargingRequestModifyRequest modifyRequest) {
        ChargingRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found or access denied"));

        if (request.getStatus() == ChargingRequestStatus.CHARGING) {
            if (modifyRequest.getMode() != null && modifyRequest.getMode() != request.getMode()) {
                throw new IllegalArgumentException("Charging request cannot change mode while charging");
            }
            if (modifyRequest.getTargetAmount() != null
                    && modifyRequest.getTargetAmount().compareTo(request.getTargetAmount()) > 0) {
                throw new IllegalArgumentException("Charging request cannot increase target amount while charging");
            }
            if (modifyRequest.getTargetAmount() != null) {
                request.setTargetAmount(modifyRequest.getTargetAmount());
            }
            ChargingRequest saved = requestRepository.save(request);
            return toDetailDto(saved, resolvePlateNumber(saved.getVehicleId()));
        }

        if (request.getStatus() != ChargingRequestStatus.WAITING
                && request.getStatus() != ChargingRequestStatus.ASSIGNED) {
            throw new IllegalArgumentException("Only waiting or assigned requests can be modified");
        }

        ChargeMode oldMode = request.getMode();
        if (modifyRequest.getTargetAmount() != null) {
            request.setTargetAmount(modifyRequest.getTargetAmount());
        }
        if (modifyRequest.getMode() != null) {
            request.setMode(modifyRequest.getMode());
        }
        request.setStatus(ChargingRequestStatus.WAITING);
        request.setAssignedPileId(null);
        request.setQueueArea(QueueArea.WAITING_AREA);
        request.setQueueNumber(null);

        ChargingRequest saved = requestRepository.save(request);
        schedulerService.triggerDispatch(oldMode);
        if (saved.getMode() != oldMode) {
            schedulerService.triggerDispatch(saved.getMode());
        }
        saved = requestRepository.findById(saved.getId()).orElseThrow();
        return toDetailDto(saved, resolvePlateNumber(saved.getVehicleId()));
    }

    private String resolvePlateNumber(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .map(Vehicle::getPlateNumber)
                .orElse(null);
    }

    private ChargingRequestDetailDTO toDetailDto(ChargingRequest request, String plateNumber) {
        ChargingRequestDetailDTO dto = new ChargingRequestDetailDTO();
        dto.setRequestId(request.getId());
        dto.setUserId(request.getUserId());
        dto.setVehicleId(request.getVehicleId());
        dto.setMode(request.getMode());
        dto.setTargetAmount(request.getTargetAmount());
        dto.setChargedAmount(request.getChargedAmount());
        dto.setStatus(request.getStatus());
        dto.setQueueNumber(request.getQueueNumber());
        dto.setQueueArea(request.getQueueArea());
        dto.setAssignedPileId(request.getAssignedPileId());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setPlateNumber(plateNumber);
        return dto;
    }
}
