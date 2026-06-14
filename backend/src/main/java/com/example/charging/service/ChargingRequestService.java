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

@Service
public class ChargingRequestService {

    private final ChargingRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    public ChargingRequestService(ChargingRequestRepository requestRepository,
                                  UserRepository userRepository,
                                  VehicleRepository vehicleRepository) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
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
        return toDetailDto(saved);
    }

    public ChargingRequestDetailDTO getRequestDetail(Long requestId, Long userId) {
        ChargingRequest request = requestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new IllegalArgumentException("请求不存在或无权访问"));
        return toDetailDto(request);
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
        return toDetailDto(saved);
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
        return toDetailDto(saved);
    }

    private Integer generateQueueNumber(ChargeMode mode) {
        List<ChargingRequest> waiting = requestRepository.findByModeAndStatusOrderByCreatedAtAsc(
                mode, ChargingRequestStatus.WAITING);
        return waiting.size() + 1;
    }

    private ChargingRequestDetailDTO toDetailDto(ChargingRequest request) {
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
        return dto;
    }
}