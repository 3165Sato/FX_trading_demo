package com.example.fx.demo.backend.position;

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

@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;

    @Enumerated(EnumType.STRING)
    private CurrencyPair currencyPair;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    private BigDecimal quantity;
    private BigDecimal averagePrice;

    protected Position() {
    }

    public Position(Long accountId, CurrencyPair currencyPair, OrderSide side, BigDecimal quantity, BigDecimal averagePrice) {
        this.accountId = accountId;
        this.currencyPair = currencyPair;
        this.side = side;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }
}
