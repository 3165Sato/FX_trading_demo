package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "currency_pairs")
public class CurrencyPair extends BaseEntity {

    private String symbol;
    private String baseCurrency;
    private String quoteCurrency;
    private Integer priceScale;
    private Integer quantityScale;
    private Boolean enabled = true;

    protected CurrencyPair() {
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    public Integer getPriceScale() {
        return priceScale;
    }

    public void setPriceScale(Integer priceScale) {
        this.priceScale = priceScale;
    }

    public Integer getQuantityScale() {
        return quantityScale;
    }

    public void setQuantityScale(Integer quantityScale) {
        this.quantityScale = quantityScale;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
