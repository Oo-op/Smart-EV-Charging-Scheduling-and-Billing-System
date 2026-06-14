package com.example.charging.controller;

import com.example.charging.dto.BillDetailDTO;
import com.example.charging.dto.BillSummaryDTO;
import com.example.charging.dto.PayBillRequest;
import com.example.charging.dto.PaymentResultDTO;
import com.example.charging.service.BillService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @GetMapping("/user/{userId}")
    public List<BillSummaryDTO> getBillsByUserId(@PathVariable Long userId) {
        return billService.getBillsByUserId(userId);
    }

    @GetMapping("/{billId}")
    public BillDetailDTO getBillDetail(@PathVariable Long billId) {
        return billService.getBillDetail(billId);
    }

    @PostMapping("/{billId}/pay")
    public PaymentResultDTO payBill(@PathVariable Long billId, @RequestBody PayBillRequest request) {
        return billService.payBill(billId, request);
    }
}
