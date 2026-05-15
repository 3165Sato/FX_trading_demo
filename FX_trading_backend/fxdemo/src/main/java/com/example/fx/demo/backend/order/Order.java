package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.common.enums.CurrencyPair;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderStatus;
import com.example.fx.demo.backend.common.enums.OrderType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;

    @Enumerated(EnumType.STRING)
    private CurrencyPair currencyPair;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.ACCEPTED;

    private BigDecimal quantity;

    private BigDecimal price;

    private Instant orderedAt = Instant.now();

    protected Order() {
    }

    public Order(Long accountId, CurrencyPair currencyPair, OrderSide side, OrderType type, BigDecimal quantity, BigDecimal price) {
        this.accountId = accountId;
        this.currencyPair = currencyPair;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public void markExecuted(BigDecimal executedPrice) {
        this.status = OrderStatus.EXECUTED;
        this.price = executedPrice;
    }
}
