package com.example.charging.repository;

import com.example.charging.entity.Payment;
import com.example.charging.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBillId(Long billId);

    List<Payment> findByUserId(Long userId);

    List<Payment> findByStatus(PaymentStatus status);
}
