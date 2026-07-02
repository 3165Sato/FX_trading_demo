package com.example.fx.demo.backend.account.repository;

import com.example.fx.demo.backend.account.domain.EquitySnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EquitySnapshotRepository extends JpaRepository<EquitySnapshot, Long> {

    List<EquitySnapshot> findByAccount_AccountNumberOrderByRecordedAtDesc(String accountNumber, Pageable pageable);

    List<EquitySnapshot> findByAccount_AccountNumberAndRecordedAtAfterOrderByRecordedAtDesc(
            String accountNumber,
            Instant recordedAt,
            Pageable pageable
    );
}
