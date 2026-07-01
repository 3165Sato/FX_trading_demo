package com.example.fx.demo.backend.cash.repository;

import com.example.fx.demo.backend.cash.domain.CashTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {
}
