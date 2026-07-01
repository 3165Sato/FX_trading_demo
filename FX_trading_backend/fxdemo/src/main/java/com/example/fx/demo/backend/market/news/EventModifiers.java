package com.example.fx.demo.backend.market.news;

import java.math.BigDecimal;

public record EventModifiers(
        BigDecimal signedJumpBps,
        double volatilityMultiplier,
        double spreadMultiplier,
        boolean clampSuppressed
) {

    public static EventModifiers neutral() {
        return new EventModifiers(BigDecimal.ZERO, 1.0, 1.0, false);
    }
}
