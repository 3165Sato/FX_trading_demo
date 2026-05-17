package com.example.fx.demo.backend.cash;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {
}
