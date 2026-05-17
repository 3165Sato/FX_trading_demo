package com.example.fx.demo.backend.account;

import com.example.fx.demo.backend.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    // CustomerとはIDで紐づける。関連の詳細設計は次の段階で検討する。
    private Long customerId;
    private String accountNumber;
    private String userName;
    private BigDecimal balance = BigDecimal.ZERO;
    private BigDecimal marginUsed = BigDecimal.ZERO;
    private BigDecimal realizedPnl = BigDecimal.ZERO;
    private String status;

    protected Account() {
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getMarginUsed() {
        return marginUsed;
    }

    public void setMarginUsed(BigDecimal marginUsed) {
        this.marginUsed = marginUsed;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
