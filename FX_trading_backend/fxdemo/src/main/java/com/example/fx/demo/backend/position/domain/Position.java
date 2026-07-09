package com.example.fx.demo.backend.position.domain;

import com.example.fx.demo.backend.common.entity.BaseEntity;
import com.example.fx.demo.backend.common.enums.PositionSide;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "positions")
public class Position extends BaseEntity {

    private Long accountId;
    private String currencyPair;

    @Enumerated(EnumType.STRING)
    private PositionSide side;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(precision = 19, scale = 8)
    private BigDecimal openPrice;

    @Enumerated(EnumType.STRING)
    private PositionStatus status = PositionStatus.OPEN;

    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Long openTradeId;
    private Long closeTradeId;

    // 旧ネッティング期の互換カラム。新しい個別建玉ではopenPriceを正とする。
    @Column(precision = 19, scale = 8)
    private BigDecimal avgPrice;

    private BigDecimal unrealizedPnl;

    @Column(precision = 19, scale = 4)
    private BigDecimal accruedSwap = BigDecimal.ZERO;

    public Position() {
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(String currencyPair) {
        this.currencyPair = currencyPair;
    }

    public PositionSide getSide() {
        return side;
    }

    public void setSide(PositionSide side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public PositionStatus getStatus() {
        return status;
    }

    public void setStatus(PositionStatus status) {
        this.status = status;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Long getOpenTradeId() {
        return openTradeId;
    }

    public void setOpenTradeId(Long openTradeId) {
        this.openTradeId = openTradeId;
    }

    public Long getCloseTradeId() {
        return closeTradeId;
    }

    public void setCloseTradeId(Long closeTradeId) {
        this.closeTradeId = closeTradeId;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public BigDecimal getUnrealizedPnl() {
        return unrealizedPnl;
    }

    public void setUnrealizedPnl(BigDecimal unrealizedPnl) {
        this.unrealizedPnl = unrealizedPnl;
    }

    public BigDecimal getAccruedSwap() {
        return accruedSwap;
    }

    public void setAccruedSwap(BigDecimal accruedSwap) {
        this.accruedSwap = accruedSwap;
    }
}
