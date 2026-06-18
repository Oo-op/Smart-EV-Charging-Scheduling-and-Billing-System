package com.example.charging.service;

import com.example.charging.dto.VehicleBindRequest;
import com.example.charging.dto.VehicleDTO;
import com.example.charging.entity.User;
import com.example.charging.entity.Vehicle;
import com.example.charging.repository.UserRepository;
import com.example.charging.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    public VehicleService(VehicleRepository vehicleRepository, UserRepository userRepository) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public VehicleDTO bindVehicle(VehicleBindRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Vehicle vehicle = new Vehicle();
        vehicle.setUserId(user.getId());
        vehicle.setPlateNumber(request.getPlateNumber());
        vehicle.setModel(request.getModel());
        vehicle.setBatteryCapacity(request.getBatteryCapacity());
        Vehicle saved = vehicleRepository.save(vehicle);

        return toDto(saved);
    }

    public List<VehicleDTO> listVehiclesByUser(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return vehicleRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void unbindVehicle(Long vehicleId, Long userId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("车辆不存在"));
        if (!vehicle.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该车辆");
        }
        vehicleRepository.delete(vehicle);
    }

    private VehicleDTO toDto(Vehicle vehicle) {
        VehicleDTO dto = new VehicleDTO();
        dto.setVehicleId(vehicle.getId());
        dto.setUserId(vehicle.getUserId());
        dto.setPlateNumber(vehicle.getPlateNumber());
        dto.setModel(vehicle.getModel());
        dto.setBatteryCapacity(vehicle.getBatteryCapacity());
        return dto;
    }
}