package com.example.fx.demo.backend.trade;

import com.example.fx.demo.backend.common.entity.BaseEntity;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.TradeKind;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
public class Trade extends BaseEntity {

    private Long orderId;
    private Long accountId;
    private String currencyPair;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(precision = 19, scale = 8)
    private BigDecimal executionPrice;

    @Enumerated(EnumType.STRING)
    private TradeKind tradeKind = TradeKind.OPEN;

    private Long positionId;

    @Column(precision = 19, scale = 8)
    private BigDecimal realizedPnl;

    private LocalDateTime executedAt;

    public Trade() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(BigDecimal executionPrice) {
        this.executionPrice = executionPrice;
    }

    public TradeKind getTradeKind() {
        return tradeKind;
    }

    public void setTradeKind(TradeKind tradeKind) {
        this.tradeKind = tradeKind;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
