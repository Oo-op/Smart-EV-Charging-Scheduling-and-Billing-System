package com.example.charging.repository;

import com.example.charging.entity.Bill;
import com.example.charging.enums.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findByUserId(Long userId);

    List<Bill> findBySessionId(Long sessionId);

    List<Bill> findByStatus(BillStatus status);

    List<Bill> findByStatusAndCreatedAtBetween(BillStatus status, LocalDateTime start, LocalDateTime end);
}
