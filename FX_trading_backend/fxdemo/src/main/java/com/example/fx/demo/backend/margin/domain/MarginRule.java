package com.example.fx.demo.backend.margin.domain;

import com.example.fx.demo.backend.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "margin_rules")
public class MarginRule extends BaseEntity {

    private String currencyPair;
    private BigDecimal leverage;
    private BigDecimal marginRate;
    private BigDecimal lossCutRate;
    private Boolean enabled = true;

    protected MarginRule() {
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(String currencyPair) {
        this.currencyPair = currencyPair;
    }

    public BigDecimal getLeverage() {
        return leverage;
    }

    public void setLeverage(BigDecimal leverage) {
        this.leverage = leverage;
    }

    public BigDecimal getMarginRate() {
        return marginRate;
    }

    public void setMarginRate(BigDecimal marginRate) {
        this.marginRate = marginRate;
    }

    public BigDecimal getLossCutRate() {
        return lossCutRate;
    }

    public void setLossCutRate(BigDecimal lossCutRate) {
        this.lossCutRate = lossCutRate;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
