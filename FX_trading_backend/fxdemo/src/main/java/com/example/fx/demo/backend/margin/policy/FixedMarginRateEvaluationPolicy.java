package com.example.fx.demo.backend.margin.policy;

import com.example.fx.demo.backend.market.rate.MarketRate;
import com.example.fx.demo.backend.position.domain.Position;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FixedMarginRateEvaluationPolicy implements MarginRateEvaluationPolicy {

    @Override
    public String name() {
        return "FIXED";
    }

    @Override
    public BigDecimal evaluateRate(Position position, MarketRate marketRate) {
        // 1bでは建玉時レートを必要証拠金の評価レートとして固定する。
        return position.getOpenPrice() == null ? position.getAvgPrice() : position.getOpenPrice();
    }
}
