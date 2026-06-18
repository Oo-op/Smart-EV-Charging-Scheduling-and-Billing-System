package com.example.charging.dto;

import lombok.Data;

@Data
public class PayBillRequest {
    private String paymentMethod; // e.g. "WECHAT", "ALIPAY"
}
