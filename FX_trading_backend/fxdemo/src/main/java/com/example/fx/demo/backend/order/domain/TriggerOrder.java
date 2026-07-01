package com.example.fx.demo.backend.order.domain;

import com.example.fx.demo.backend.common.entity.BaseEntity;
import com.example.fx.demo.backend.common.enums.ExitOrderType;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.TriggerOrderPurpose;
import com.example.fx.demo.backend.common.enums.TriggerOrderStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

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

    @Column(precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(precision = 19, scale = 8)
    private BigDecimal triggerPrice;

    @Enumerated(EnumType.STRING)
    private TriggerOrderStatus status;

    private LocalDateTime triggeredAt;
    private Long resultingOrderId;
    private String rejectionReason;
    private Long targetPositionId;
    private Long parentOrderId;
    private String ocoGroupId;

    @Enumerated(EnumType.STRING)
    private TriggerOrderPurpose purpose;

    @Enumerated(EnumType.STRING)
    private ExitOrderType exitType;

    public TriggerOrder() {
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

    public Long getResultingOrderId() {
        return resultingOrderId;
    }

    public void setResultingOrderId(Long resultingOrderId) {
        this.resultingOrderId = resultingOrderId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Long getTargetPositionId() {
        return targetPositionId;
    }

    public void setTargetPositionId(Long targetPositionId) {
        this.targetPositionId = targetPositionId;
    }

    public Long getParentOrderId() {
        return parentOrderId;
    }

    public void setParentOrderId(Long parentOrderId) {
        this.parentOrderId = parentOrderId;
    }

    public String getOcoGroupId() {
        return ocoGroupId;
    }

    public void setOcoGroupId(String ocoGroupId) {
        this.ocoGroupId = ocoGroupId;
    }

    public TriggerOrderPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(TriggerOrderPurpose purpose) {
        this.purpose = purpose;
    }

    public ExitOrderType getExitType() {
        return exitType;
    }

    public void setExitType(ExitOrderType exitType) {
        this.exitType = exitType;
    }
}
