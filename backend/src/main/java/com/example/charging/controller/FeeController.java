package com.example.charging.controller;

import com.example.charging.dto.FeeCalcRequest;
import com.example.charging.dto.FeeCalcResult;
import com.example.charging.entity.ElectricityPrice;
import com.example.charging.service.FeeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 计费接口：
 *   POST /api/fees/calc      按时间区间 + 电量 计费（支持跨时段）
 *   GET  /api/fees/prices    列出所有电价配置
 */
@RestController
@RequestMapping("/fees")
public class FeeController {

    private final FeeService feeService;

    public FeeController(FeeService feeService) {
        this.feeService = feeService;
    }

    @PostMapping("/calc")
    public FeeCalcResult calc(@RequestBody FeeCalcRequest req) {
        return feeService.calculate(req);
    }

    @GetMapping("/prices")
    public List<ElectricityPrice> prices() {
        return feeService.listPrices();
    }
}
