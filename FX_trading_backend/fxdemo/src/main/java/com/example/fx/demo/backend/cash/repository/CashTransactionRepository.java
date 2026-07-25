package com.example.fx.demo.backend.cash.repository;

import com.example.fx.demo.backend.cash.domain.CashTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {
    boolean existsByAccountId(Long accountId);

    List<CashTransaction> findByAccountIdOrderByCompletedAtDesc(Long accountId, Pageable pageable);
}
