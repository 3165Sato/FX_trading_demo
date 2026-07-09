package com.example.fx.demo.backend.market.swap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "demofx.swap")
public class SwapProperties {

    private boolean enabled = true;
    private LocalTime rolloverTime = LocalTime.of(6, 0);
    private Map<String, SwapRate> rates = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalTime getRolloverTime() {
        return rolloverTime;
    }

    public void setRolloverTime(LocalTime rolloverTime) {
        this.rolloverTime = rolloverTime;
    }

    public Map<String, SwapRate> getRates() {
        return rates;
    }

    public void setRates(Map<String, SwapRate> rates) {
        this.rates = rates;
    }

    public static class SwapRate {
        private BigDecimal longRate = BigDecimal.ZERO;
        private BigDecimal shortRate = BigDecimal.ZERO;

        public BigDecimal getLongRate() {
            return longRate;
        }

        public void setLongRate(BigDecimal longRate) {
            this.longRate = longRate;
        }

        public BigDecimal getShortRate() {
            return shortRate;
        }

        public void setShortRate(BigDecimal shortRate) {
            this.shortRate = shortRate;
        }
    }
}
