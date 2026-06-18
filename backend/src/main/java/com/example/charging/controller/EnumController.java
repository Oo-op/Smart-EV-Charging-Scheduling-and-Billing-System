package com.example.charging.controller;

import com.example.charging.enums.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一状态枚举查询接口：前端通过 /api/enums 一次性拉取全部 code -> desc 映射。
 */
@RestController
@RequestMapping("/enums")
public class EnumController {

    @GetMapping
    public Map<String, List<EnumEntry>> all() {
        return Map.of(
                "stationStatus", toEntries(StationStatus.values()),
                "chargingRequestStatus", toEntries(ChargingRequestStatus.values()),
                "chargingPileStatus", toEntries(ChargingPileStatus.values()),
                "chargingSessionStatus", toEntries(ChargingSessionStatus.values()),
                "billStatus", toEntries(BillStatus.values()),
                "chargeMode", toEntries(ChargeMode.values()),
                "paymentStatus", toEntries(PaymentStatus.values()),
                "pricePeriod", toEntries(PricePeriod.values())
        );
    }

    private <E extends Enum<E>> List<EnumEntry> toEntries(E[] values) {
        return Arrays.stream(values)
                .map(v -> {
                    try {
                        String code = (String) v.getClass().getMethod("getCode").invoke(v);
                        String desc = (String) v.getClass().getMethod("getDesc").invoke(v);
                        return new EnumEntry(code, desc);
                    } catch (Exception e) {
                        return new EnumEntry(v.name(), v.name());
                    }
                })
                .collect(Collectors.toList());
    }

    public record EnumEntry(String code, String desc) {
    }
}
