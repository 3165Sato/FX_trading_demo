package com.example.fx.demo.backend.margin;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MarginService {

    private static final BigDecimal DEFAULT_LEVERAGE = new BigDecimal("25");

    public BigDecimal requiredMargin(BigDecimal notionalAmount) {
        // 必要証拠金 = 取引金額 / レバレッジ。税制や商品仕様は扱わない学習用の単純化。
        return notionalAmount.divide(DEFAULT_LEVERAGE, 2, RoundingMode.HALF_UP);
    }
}
