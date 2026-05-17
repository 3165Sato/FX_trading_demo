package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.common.entity.BaseEntity;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.TriggerOrderStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trigger_orders")
public class TriggerOrder extends BaseEntity {

    private Long accountId;
    private String currencyPair;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    private BigDecimal quantity;
    private BigDecimal triggerPrice;

    @Enumerated(EnumType.STRING)
    private TriggerOrderStatus status;

    private LocalDateTime triggeredAt;

    protected TriggerOrder() {
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

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public TriggerOrderStatus getStatus() {
        return status;
    }

    public void setStatus(TriggerOrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }
}
