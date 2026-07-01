package com.example.fx.demo.backend.position.model;

public record CurrencyPairScale(
        String quoteCurrency,
        int priceScale,
        int quantityScale
) {
    public int pnlScale() {
        return "JPY".equals(quoteCurrency) ? 0 : 2;
    }
}
