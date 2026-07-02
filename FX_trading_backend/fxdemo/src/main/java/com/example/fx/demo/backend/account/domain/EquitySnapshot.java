package com.example.fx.demo.backend.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "equity_snapshots")
public class EquitySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // デモ口座の資産状態を時系列で記録する。履歴なので更新は行わない。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal equity;

    @Column(precision = 19, scale = 4)
    private BigDecimal usedMargin;

    @Column(precision = 19, scale = 4)
    private BigDecimal marginRatio;

    @Column(nullable = false)
    private Instant recordedAt;

    public EquitySnapshot() {
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getEquity() {
        return equity;
    }

    public void setEquity(BigDecimal equity) {
        this.equity = equity;
    }

    public BigDecimal getUsedMargin() {
        return usedMargin;
    }

    public void setUsedMargin(BigDecimal usedMargin) {
        this.usedMargin = usedMargin;
    }

    public BigDecimal getMarginRatio() {
        return marginRatio;
    }

    public void setMarginRatio(BigDecimal marginRatio) {
        this.marginRatio = marginRatio;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
