package com.example.fx.demo.backend.margin;

import com.example.fx.demo.backend.market.MarketRate;
import com.example.fx.demo.backend.position.Position;

import java.math.BigDecimal;

public interface MarginRateEvaluationPolicy {

    String name();

    BigDecimal evaluateRate(Position position, MarketRate marketRate);
}
