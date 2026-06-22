package com.example.fx.demo.backend.margin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "demofx.margin")
public class MarginProperties {

    private LossCut lossCut = new LossCut();
    private Order order = new Order();

    public LossCut getLossCut() {
        return lossCut;
    }

    public void setLossCut(LossCut lossCut) {
        this.lossCut = lossCut;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public static class LossCut {
        private boolean enabled = true;
        private BigDecimal thresholdPercent = new BigDecimal("50");
        private BigDecimal warningPercent = new BigDecimal("100");
        private int evaluationIntervalSeconds = 2;
        private String liquidation = "ALL";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public BigDecimal getThresholdPercent() {
            return thresholdPercent;
        }

        public void setThresholdPercent(BigDecimal thresholdPercent) {
            this.thresholdPercent = thresholdPercent;
        }

        public BigDecimal getWarningPercent() {
            return warningPercent;
        }

        public void setWarningPercent(BigDecimal warningPercent) {
            this.warningPercent = warningPercent;
        }

        public int getEvaluationIntervalSeconds() {
            return evaluationIntervalSeconds;
        }

        public void setEvaluationIntervalSeconds(int evaluationIntervalSeconds) {
            this.evaluationIntervalSeconds = evaluationIntervalSeconds;
        }

        public String getLiquidation() {
            return liquidation;
        }

        public void setLiquidation(String liquidation) {
            this.liquidation = liquidation;
        }

        public long evaluationIntervalMillis() {
            return Math.max(1, evaluationIntervalSeconds) * 1000L;
        }
    }

    public static class Order {
        private boolean marginCheckEnabled = true;

        public boolean isMarginCheckEnabled() {
            return marginCheckEnabled;
        }

        public void setMarginCheckEnabled(boolean marginCheckEnabled) {
            this.marginCheckEnabled = marginCheckEnabled;
        }
    }
}
