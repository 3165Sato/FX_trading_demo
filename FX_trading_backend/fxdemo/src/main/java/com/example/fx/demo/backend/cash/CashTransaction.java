package com.example.fx.demo.backend.cash;

import com.example.fx.demo.backend.common.entity.BaseEntity;
import com.example.fx.demo.backend.common.enums.CashTransactionStatus;
import com.example.fx.demo.backend.common.enums.CashTransactionType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_transactions")
public class CashTransaction extends BaseEntity {

    private Long accountId;

    @Enumerated(EnumType.STRING)
    private CashTransactionType transactionType;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private CashTransactionStatus status;

    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;

    protected CashTransaction() {
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public CashTransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(CashTransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public CashTransactionStatus getStatus() {
        return status;
    }

    public void setStatus(CashTransactionStatus status) {
        this.status = status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
