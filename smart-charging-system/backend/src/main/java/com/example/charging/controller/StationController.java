package com.example.charging.controller;

import com.example.charging.common.ApiResponse;
import com.example.charging.dto.StationDTO;
import com.example.charging.entity.ChargingStation;
import com.example.charging.repository.ChargingStationRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 示例 Controller：演示统一返回格式。
 * 返回 List<StationDTO> 会被自动包装为：
 * { "code": 0, "message": "success", "data": [ ... ] }
 */
@RestController
@RequestMapping("/stations")
public class StationController {

    private final ChargingStationRepository stationRepository;

    public StationController(ChargingStationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @GetMapping
    public List<StationDTO> list() {
        List<ChargingStation> stations = stationRepository.findAll();
        return stations.stream().map(s -> {
            StationDTO dto = new StationDTO();
            BeanUtils.copyProperties(s, dto);
            return dto;
        }).collect(Collectors.toList());
    }

    @GetMapping("/manual")
    public ApiResponse<StationDTO> manual() {
        StationDTO dto = new StationDTO();
        dto.setName("示例充电站");
        dto.setAddress("测试地址");
        dto.setStatus("AVAILABLE");
        return ApiResponse.success(dto);
    }
}
