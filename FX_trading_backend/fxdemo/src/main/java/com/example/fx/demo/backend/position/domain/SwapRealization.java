package com.example.fx.demo.backend.position.domain;

import com.example.fx.demo.backend.common.entity.BaseEntity;
import com.example.fx.demo.backend.common.enums.SwapRealizationSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "swap_realizations")
public class SwapRealization extends BaseEntity {

    private Long accountId;
    private Long positionId;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private SwapRealizationSource source;

    private LocalDateTime realizedAt;

    public SwapRealization() {
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public SwapRealizationSource getSource() {
        return source;
    }

    public void setSource(SwapRealizationSource source) {
        this.source = source;
    }

    public LocalDateTime getRealizedAt() {
        return realizedAt;
    }

    public void setRealizedAt(LocalDateTime realizedAt) {
        this.realizedAt = realizedAt;
    }
}
