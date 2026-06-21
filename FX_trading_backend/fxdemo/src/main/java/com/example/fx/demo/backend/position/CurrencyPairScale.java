package com.example.fx.demo.backend.position;

record CurrencyPairScale(
        String quoteCurrency,
        int priceScale,
        int quantityScale
) {
    int pnlScale() {
        return "JPY".equals(quoteCurrency) ? 0 : 2;
    }
}
