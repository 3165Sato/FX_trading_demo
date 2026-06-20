package com.example.fx.demo.backend.trade.dto;

public record OrderResultResponse(
        OrderSummaryResponse order,
        TradeSummaryResponse trade
) {
}
