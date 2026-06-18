package com.example.charging.service;

import com.example.charging.dto.ChargingRequestDetailDTO;
import com.example.charging.dto.ChargingRequestModifyRequest;
import com.example.charging.dto.ChargingRequestSubmitRequest;
import com.example.charging.entity.ChargingRequest;
import com.example.charging.entity.User;
import com.example.charging.entity.Vehicle;
import com.example.charging.enums.ChargeMode;
import com.example.charging.enums.ChargingRequestStatus;
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
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> new IllegalArgumentException("车辆不存在"));
        if (!vehicle.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("车辆不属于该用户");
        }

        int totalOpenQueueSlots = schedulerService.totalOpenQueueSlots(request.getMode());
        if (totalOpenQueueSlots <= 0) {
            throw new IllegalArgumentException("当前模式暂未开放，请联系管理员");
        }
        int waitingCount = requestRepository.findByModeAndStatusOrderByCreatedAtAsc(
                request.getMode(), ChargingRequestStatus.WAITING).size();
        if (waitingCount >= totalOpenQueueSlots) {
            throw new IllegalArgumentException("当前模式开放的排队位置已满，请稍后再试");
        }

        ChargingRequest req = new ChargingRequest();
        req.setUserId(user.getId());
        req.setVehicleId(vehicle.getId());
        req.setMode(request.getMode());
        req.setTargetAmount(request.getTargetAmount());
        req.setChargedAmount(BigDecimal.ZERO);
        req.setStatus(ChargingRequestStatus.WAITING);
        req.setQueueNumber(generateQueueNumber(request.getMode()));
        req.setAssignedPileId(null);

        ChargingRequest saved = requestRepository.save(req);
        return toDetailDto(saved, vehicle.getPlateNumber());
    }

    public ChargingRequestDetailDTO getRequestDetail(Long requestId, Long userId) {
        ChargingRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new IllegalArgumentException("请求不存在或无权访问"));
        return toDetailDto(request, resolvePlateNumber(request.getVehicleId()));
    }

    public List<ChargingRequestDetailDTO> listByUserId(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        List<ChargingRequest> requests = requestRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Vehicle> vehicles = vehicleRepository.findAllById(
                        requests.stream().map(ChargingRequest::getVehicleId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));
        return requests.stream()
                .map(req -> {
                    Vehicle vehicle = vehicles.get(req.getVehicleId());
                    String plate = vehicle == null ? null : vehicle.getPlateNumber();
                    return toDetailDto(req, plate);
                })
                .toList();
    }

    @Transactional
    public ChargingRequestDetailDTO cancelRequest(Long requestId, Long userId) {
        ChargingRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new IllegalArgumentException("请求不存在或无权访问"));
        if (request.getStatus() != ChargingRequestStatus.WAITING && request.getStatus() != ChargingRequestStatus.ASSIGNED) {
            throw new IllegalArgumentException("只有等待中或已分配状态的请求可以取消");
        }
        request.setStatus(ChargingRequestStatus.CANCELLED);
        ChargingRequest saved = requestRepository.save(request);
        return toDetailDto(saved, resolvePlateNumber(saved.getVehicleId()));
    }

    @Transactional
    public ChargingRequestDetailDTO modifyRequest(Long requestId, Long userId, ChargingRequestModifyRequest modifyRequest) {
        ChargingRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new IllegalArgumentException("请求不存在或无权访问"));
        if (request.getStatus() != ChargingRequestStatus.WAITING) {
            throw new IllegalArgumentException("只有等待中的请求可以修改");
        }

        if (modifyRequest.getTargetAmount() != null) {
            request.setTargetAmount(modifyRequest.getTargetAmount());
        }
        // 第一版暂不支持修改 mode，需要重新排队，比较复杂，可作为增强功能
        if (modifyRequest.getMode() != null && modifyRequest.getMode() != request.getMode()) {
            throw new IllegalArgumentException("第一版不支持修改充电模式，请取消后重新提交");
        }

        ChargingRequest saved = requestRepository.save(request);
        return toDetailDto(saved, resolvePlateNumber(saved.getVehicleId()));
    }

    private Integer generateQueueNumber(ChargeMode mode) {
        List<ChargingRequest> waiting = requestRepository.findByModeAndStatusOrderByCreatedAtAsc(
                mode, ChargingRequestStatus.WAITING);
        return waiting.size() + 1;
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
        dto.setAssignedPileId(request.getAssignedPileId());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setPlateNumber(plateNumber);
        return dto;
    }
}
