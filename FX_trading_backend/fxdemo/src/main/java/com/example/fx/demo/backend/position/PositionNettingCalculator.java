package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.position.dto.PositionResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PositionNettingCalculator {

    private static final CurrencyPairScale DEFAULT_SCALE = new CurrencyPairScale(8, 4);

    List<PositionResponse> calculate(
            List<PositionTradeInput> trades,
            Map<String, CurrencyPairScale> scales
    ) {
        Map<String, PositionAccumulator> accumulators = new HashMap<>();

        for (PositionTradeInput trade : trades) {
            CurrencyPairScale scale = scales.getOrDefault(trade.currencyPair(), DEFAULT_SCALE);
            PositionAccumulator accumulator = accumulators.computeIfAbsent(
                    trade.currencyPair(),
                    ignored -> new PositionAccumulator()
            );
            accumulator.apply(trade, scale);
        }

        return accumulators.entrySet().stream()
                .filter(entry -> entry.getValue().hasOpenPosition())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().toResponse(entry.getKey(), scales.getOrDefault(entry.getKey(), DEFAULT_SCALE)))
                .toList();
    }

    private static final class PositionAccumulator {
        private BigDecimal signedQuantity = BigDecimal.ZERO;
        private BigDecimal averagePrice = BigDecimal.ZERO;
        private LocalDateTime updatedAt;

        private void apply(PositionTradeInput trade, CurrencyPairScale scale) {
            BigDecimal tradeQuantity = signedQuantity(trade);
            BigDecimal tradePrice = trade.executionPrice().setScale(scale.priceScale(), RoundingMode.HALF_UP);

            if (signedQuantity.signum() == 0) {
                signedQuantity = tradeQuantity;
                averagePrice = tradePrice;
            } else if (signedQuantity.signum() == tradeQuantity.signum()) {
                averagePrice = weightedAverage(tradeQuantity, tradePrice, scale.priceScale());
                signedQuantity = signedQuantity.add(tradeQuantity);
            } else {
                applyOppositeSideTrade(tradeQuantity, tradePrice);
            }

            updatedAt = trade.executedAt();
        }

        private BigDecimal weightedAverage(BigDecimal tradeQuantity, BigDecimal tradePrice, int priceScale) {
            BigDecimal currentAbs = signedQuantity.abs();
            BigDecimal tradeAbs = tradeQuantity.abs();
            BigDecimal totalAbs = currentAbs.add(tradeAbs);
            return currentAbs.multiply(averagePrice)
                    .add(tradeAbs.multiply(tradePrice))
                    .divide(totalAbs, priceScale, RoundingMode.HALF_UP);
        }

        private void applyOppositeSideTrade(BigDecimal tradeQuantity, BigDecimal tradePrice) {
            BigDecimal currentAbs = signedQuantity.abs();
            BigDecimal tradeAbs = tradeQuantity.abs();
            int comparison = tradeAbs.compareTo(currentAbs);

            if (comparison < 0) {
                // 部分決済では残った建玉の平均建値を据え置く。
                signedQuantity = signedQuantity.add(tradeQuantity);
            } else if (comparison == 0) {
                signedQuantity = BigDecimal.ZERO;
                averagePrice = BigDecimal.ZERO;
            } else {
                // ドテン時は超過分を反対サイドの新規建玉として約定価格で持つ。
                signedQuantity = BigDecimal.valueOf(tradeQuantity.signum()).multiply(tradeAbs.subtract(currentAbs));
                averagePrice = tradePrice;
            }
        }

        private boolean hasOpenPosition() {
            return signedQuantity.signum() != 0;
        }

        private PositionResponse toResponse(String currencyPair, CurrencyPairScale scale) {
            return new PositionResponse(
                    currencyPair,
                    signedQuantity.signum() > 0 ? "LONG" : "SHORT",
                    signedQuantity.abs().setScale(scale.quantityScale(), RoundingMode.HALF_UP),
                    averagePrice.setScale(scale.priceScale(), RoundingMode.HALF_UP),
                    updatedAt
            );
        }

        private BigDecimal signedQuantity(PositionTradeInput trade) {
            return trade.side() == OrderSide.BUY ? trade.quantity() : trade.quantity().negate();
        }
    }
}
