package com.example.fx.demo.backend.position.calculation;

import com.example.fx.demo.backend.position.model.CurrencyPairScale;
import com.example.fx.demo.backend.position.model.PositionSnapshot;
import com.example.fx.demo.backend.position.dto.PositionResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class PositionValuationCalculator {

    public PositionResponse toResponse(
            PositionSnapshot position,
            BigDecimal bid,
            BigDecimal ask,
            CurrencyPairScale scale
    ) {
        return toResponse(position, bid, ask, scale, null);
    }

    public PositionResponse toResponse(
            PositionSnapshot position,
            BigDecimal bid,
            BigDecimal ask,
            CurrencyPairScale scale,
            BigDecimal requiredMargin
    ) {
        BigDecimal currentPrice = currentPrice(position, bid, ask);
        BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, currentPrice, scale);
        return new PositionResponse(
                null,
                position.currencyPair(),
                position.side(),
                position.quantity(),
                position.averagePrice(),
                position.quoteCurrency(),
                currentPrice,
                unrealizedPnl,
                position.updatedAt(),
                requiredMargin,
                position.updatedAt(),
                List.of()
        );
    }

    public BigDecimal calculateUnrealizedPnl(
            PositionSnapshot position,
            BigDecimal currentPrice,
            CurrencyPairScale scale
    ) {
        if (currentPrice == null || scale == null) {
            return null;
        }
        BigDecimal pnl = "LONG".equals(position.side())
                ? currentPrice.subtract(position.averagePrice()).multiply(position.quantity())
                : position.averagePrice().subtract(currentPrice).multiply(position.quantity());
        return pnl.setScale(scale.pnlScale(), RoundingMode.HALF_UP);
    }

    private BigDecimal currentPrice(PositionSnapshot position, BigDecimal bid, BigDecimal ask) {
        return "LONG".equals(position.side()) ? bid : ask;
    }
}
