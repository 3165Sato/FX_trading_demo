package com.example.fx.demo.backend.margin;

import com.example.fx.demo.backend.common.enums.OrderSide;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ValuationService {

    public BigDecimal unrealizedProfitLoss(OrderSide side, BigDecimal quantity, BigDecimal averagePrice, BigDecimal currentPrice) {
        BigDecimal priceDifference = currentPrice.subtract(averagePrice);
        BigDecimal signedDifference = side == OrderSide.BUY ? priceDifference : priceDifference.negate();
        // 学習用なので通貨換算やスワップポイントはまだ考慮しない。
        return signedDifference.multiply(quantity);
    }
}
