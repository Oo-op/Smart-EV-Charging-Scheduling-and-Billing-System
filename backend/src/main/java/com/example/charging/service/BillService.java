package com.example.charging.service;

import com.example.charging.dto.*;
import com.example.charging.entity.*;
import com.example.charging.enums.*;
import com.example.charging.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BillService {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final FeeService feeService;
    private final ChargingPileRepository pileRepository;
    private final ChargingSessionRepository sessionRepository;

    public BillService(BillRepository billRepository,
                       PaymentRepository paymentRepository,
                       FeeService feeService,
                       ChargingPileRepository pileRepository,
                       ChargingSessionRepository sessionRepository) {
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.feeService = feeService;
        this.pileRepository = pileRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 根据充电会话和充电桩信息生成精细化的分时账单。
     * 调用已有的 FeeService.calculate(...) 实现按小时划分的峰平谷电价计算。
     */
    @Transactional
    public Bill generateBill(ChargingSession session) {
        ChargingPile pile = pileRepository.findById(session.getPileId())
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));

        FeeCalcRequest feeCalcRequest = FeeCalcRequest.builder()
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .energyKwh(session.getChargedAmount())
                .mode(pile.getMode())
                .build();

        FeeCalcResult feeCalcResult = feeService.calculate(feeCalcRequest);

        Bill bill = new Bill();
        bill.setUserId(session.getUserId());
        bill.setSessionId(session.getId());
        bill.setElectricityFee(feeCalcResult.getElectricityFee());
        bill.setServiceFee(feeCalcResult.getServiceFee());
        bill.setTotalFee(feeCalcResult.getTotalFee());
        bill.setBreakdown(feeCalcResult.getBreakdown());
        bill.setStatus(BillStatus.UNPAID);

        return billRepository.save(bill);
    }

    /**
     * 查询某用户的所有账单列表。
     */
    public List<BillSummaryDTO> getBillsByUserId(Long userId) {
        List<Bill> bills = billRepository.findByUserId(userId);
        return bills.stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    /**
     * 查询单个账单的详细信息，包含关联的充电模式、充电电量以及分时详情。
     */
    public BillDetailDTO getBillDetail(Long billId) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("账单不存在"));
        ChargingSession session = sessionRepository.findById(bill.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("充电会话不存在"));
        ChargingPile pile = pileRepository.findById(session.getPileId())
                .orElseThrow(() -> new IllegalArgumentException("充电桩不存在"));

        BillDetailDTO dto = new BillDetailDTO();
        dto.setBillId(bill.getId());
        dto.setUserId(bill.getUserId());
        dto.setSessionId(bill.getSessionId());
        dto.setPileCode(pile.getCode());
        dto.setMode(pile.getMode().name());
        dto.setChargedAmount(session.getChargedAmount());
        dto.setElectricityFee(bill.getElectricityFee());
        dto.setServiceFee(bill.getServiceFee());
        dto.setTotalFee(bill.getTotalFee());
        dto.setStatus(bill.getStatus());
        dto.setCreatedAt(bill.getCreatedAt());
        dto.setBreakdown(bill.getBreakdown());
        return dto;
    }

    /**
     * 模拟支付接口。
     * 目前仅做模拟支付，修改账单状态为 PAID，并记录 Payment 记录。
     */
    @Transactional
    public PaymentResultDTO payBill(Long billId, PayBillRequest req) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new IllegalArgumentException("账单不存在"));

        if (bill.getStatus() != BillStatus.UNPAID) {
            throw new IllegalArgumentException("账单必须处于未支付状态");
        }

        // 修改账单状态
        bill.setStatus(BillStatus.PAID);
        billRepository.save(bill);

        // 创建 Payment 支付记录
        Payment payment = new Payment();
        payment.setBillId(bill.getId());
        payment.setUserId(bill.getUserId());
        payment.setPaymentMethod(req.getPaymentMethod());
        payment.setAmount(bill.getTotalFee());
        payment.setStatus(PaymentStatus.SUCCESS);

        // TODO: 如果需要真实支付，需要在 User 实体类中扩展 balance 字段并实现真实余额扣减逻辑

        payment.setPaidAt(LocalDateTime.now());
        Payment savedPayment = paymentRepository.save(payment);

        // 返回结果
        PaymentResultDTO result = new PaymentResultDTO();
        result.setBillId(bill.getId());
        result.setPaymentId(savedPayment.getId());
        result.setPaymentMethod(savedPayment.getPaymentMethod());
        result.setAmount(savedPayment.getAmount());
        result.setBillStatus(bill.getStatus().name());
        result.setPaymentStatus(savedPayment.getStatus().name());
        result.setPaidAt(savedPayment.getPaidAt());
        return result;
    }

    private BillSummaryDTO toSummaryDto(Bill bill) {
        BillSummaryDTO dto = new BillSummaryDTO();
        dto.setBillId(bill.getId());
        dto.setElectricityFee(bill.getElectricityFee());
        dto.setServiceFee(bill.getServiceFee());
        dto.setTotalFee(bill.getTotalFee());
        dto.setStatus(bill.getStatus());
        return dto;
    }
}
