package com.example.charging.dto;

import com.example.charging.enums.ChargeMode;
import lombok.Data;

@Data
public class DispatchRequest {

    private ChargeMode mode;
}
