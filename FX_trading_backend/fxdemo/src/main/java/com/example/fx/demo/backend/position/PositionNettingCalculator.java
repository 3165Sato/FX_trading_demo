package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.common.enums.OrderSide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

class PositionNettingCalculator {

    private static final CurrencyPairScale DEFAULT_SCALE = new CurrencyPairScale("USD", 8, 4);

    PositionCalculationResult calculate(
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

        List<PositionSnapshot> openPositions = accumulators.entrySet().stream()
                .filter(entry -> entry.getValue().hasOpenPosition())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getValue().toSnapshot(entry.getKey(), scales.getOrDefault(entry.getKey(), DEFAULT_SCALE)))
                .toList();
        Map<String, BigDecimal> realizedByCurrency = summarizeRealized(accumulators, scales);

        return new PositionCalculationResult(openPositions, realizedByCurrency);
    }

    private Map<String, BigDecimal> summarizeRealized(
            Map<String, PositionAccumulator> accumulators,
            Map<String, CurrencyPairScale> scales
    ) {
        Map<String, BigDecimal> summary = new TreeMap<>();
        for (Map.Entry<String, PositionAccumulator> entry : accumulators.entrySet()) {
            CurrencyPairScale scale = scales.getOrDefault(entry.getKey(), DEFAULT_SCALE);
            BigDecimal realized = entry.getValue().realizedPnl.setScale(scale.pnlScale(), RoundingMode.HALF_UP);
            if (realized.signum() == 0) {
                continue;
            }
            summary.merge(scale.quoteCurrency(), realized, BigDecimal::add);
        }
        return summary;
    }

    private static final class PositionAccumulator {
        private BigDecimal signedQuantity = BigDecimal.ZERO;
        private BigDecimal averagePrice = BigDecimal.ZERO;
        private BigDecimal realizedPnl = BigDecimal.ZERO;
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
            BigDecimal closeQuantity = currentAbs.min(tradeAbs);
            if (signedQuantity.signum() > 0) {
                realizedPnl = realizedPnl.add(tradePrice.subtract(averagePrice).multiply(closeQuantity));
            } else {
                realizedPnl = realizedPnl.add(averagePrice.subtract(tradePrice).multiply(closeQuantity));
            }
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

        private PositionSnapshot toSnapshot(String currencyPair, CurrencyPairScale scale) {
            return new PositionSnapshot(
                    currencyPair,
                    signedQuantity.signum() > 0 ? "LONG" : "SHORT",
                    signedQuantity.abs().setScale(scale.quantityScale(), RoundingMode.HALF_UP),
                    averagePrice.setScale(scale.priceScale(), RoundingMode.HALF_UP),
                    scale.quoteCurrency(),
                    updatedAt
            );
        }

        private BigDecimal signedQuantity(PositionTradeInput trade) {
            return trade.side() == OrderSide.BUY ? trade.quantity() : trade.quantity().negate();
        }
    }
}
