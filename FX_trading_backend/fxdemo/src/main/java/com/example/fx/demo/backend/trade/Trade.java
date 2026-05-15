package com.example.fx.demo.backend.trade;

import com.example.fx.demo.backend.common.enums.CurrencyPair;
import com.example.fx.demo.backend.common.enums.OrderSide;
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
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private Long accountId;

    @Enumerated(EnumType.STRING)
    private CurrencyPair currencyPair;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    private BigDecimal quantity;
    private BigDecimal executedPrice;
    private Instant executedAt = Instant.now();

    protected Trade() {
    }

    public Trade(Long orderId, Long accountId, CurrencyPair currencyPair, OrderSide side, BigDecimal quantity, BigDecimal executedPrice) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.currencyPair = currencyPair;
        this.side = side;
        this.quantity = quantity;
        this.executedPrice = executedPrice;
    }

    public Long getId() {
        return id;
    }
}
