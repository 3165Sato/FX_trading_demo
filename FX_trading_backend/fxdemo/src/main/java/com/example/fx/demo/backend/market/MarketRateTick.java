package com.example.fx.demo.backend.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_rate_ticks")
public class MarketRateTick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Chart history rate for each currency pair.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_pair_id", nullable = false)
    private CurrencyPair currencyPair;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal bid;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal ask;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal midPrice;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal spread;

    @Column(nullable = false)
    private Instant quotedAt;

    private Instant createdAt;

    protected MarketRateTick() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(CurrencyPair currencyPair) {
        this.currencyPair = currencyPair;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public void setBid(BigDecimal bid) {
        this.bid = bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public void setAsk(BigDecimal ask) {
        this.ask = ask;
    }

    public BigDecimal getMidPrice() {
        return midPrice;
    }

    public void setMidPrice(BigDecimal midPrice) {
        this.midPrice = midPrice;
    }

    public BigDecimal getSpread() {
        return spread;
    }

    public void setSpread(BigDecimal spread) {
        this.spread = spread;
    }

    public Instant getQuotedAt() {
        return quotedAt;
    }

    public void setQuotedAt(Instant quotedAt) {
        this.quotedAt = quotedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
