package com.example.fx.demo.backend.margin.policy;

import com.example.fx.demo.backend.market.rate.MarketRate;
import com.example.fx.demo.backend.position.domain.Position;

import java.math.BigDecimal;

public interface MarginRateEvaluationPolicy {

    String name();

    BigDecimal evaluateRate(Position position, MarketRate marketRate);
}
