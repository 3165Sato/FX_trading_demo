package com.example.fx.demo.backend.banking.repository;

import com.example.fx.demo.backend.banking.domain.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
}
