package com.example.fx.demo.backend.market.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "demofx.alerts")
public class AnomalyAlertProperties {

    private boolean enabled = true;
    private int evaluationIntervalSeconds = 3;
    private History history = new History();
    private Rules rules = new Rules();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getEvaluationIntervalSeconds() {
        return evaluationIntervalSeconds;
    }

    public void setEvaluationIntervalSeconds(int evaluationIntervalSeconds) {
        this.evaluationIntervalSeconds = evaluationIntervalSeconds;
    }

    public long evaluationIntervalMillis() {
        return Math.max(1, evaluationIntervalSeconds) * 1000L;
    }

    public History getHistory() {
        return history;
    }

    public void setHistory(History history) {
        this.history = history;
    }

    public Rules getRules() {
        return rules;
    }

    public void setRules(Rules rules) {
        this.rules = rules;
    }

    public static class History {
        private int maxEntries = 100;

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }

    public static class Rules {
        private SpreadRule spread = new SpreadRule();
        private RapidMoveRule rapidMove = new RapidMoveRule();
        private StaleDataRule staleData = new StaleDataRule();
        private CrossedQuoteRule crossedQuote = new CrossedQuoteRule();
        private VolatilitySurgeRule volatilitySurge = new VolatilitySurgeRule();

        public SpreadRule getSpread() {
            return spread;
        }

        public void setSpread(SpreadRule spread) {
            this.spread = spread;
        }

        public RapidMoveRule getRapidMove() {
            return rapidMove;
        }

        public void setRapidMove(RapidMoveRule rapidMove) {
            this.rapidMove = rapidMove;
        }

        public StaleDataRule getStaleData() {
            return staleData;
        }

        public void setStaleData(StaleDataRule staleData) {
            this.staleData = staleData;
        }

        public CrossedQuoteRule getCrossedQuote() {
            return crossedQuote;
        }

        public void setCrossedQuote(CrossedQuoteRule crossedQuote) {
            this.crossedQuote = crossedQuote;
        }

        public VolatilitySurgeRule getVolatilitySurge() {
            return volatilitySurge;
        }

        public void setVolatilitySurge(VolatilitySurgeRule volatilitySurge) {
            this.volatilitySurge = volatilitySurge;
        }
    }

    public static class SpreadRule {
        private boolean enabled = true;
        private int limit = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }
    }

    public static class RapidMoveRule {
        private boolean enabled = true;
        private int lookbackSeconds = 15;
        private BigDecimal warnBps = new BigDecimal("30");
        private BigDecimal criticalBps = new BigDecimal("60");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLookbackSeconds() {
            return lookbackSeconds;
        }

        public void setLookbackSeconds(int lookbackSeconds) {
            this.lookbackSeconds = lookbackSeconds;
        }

        public BigDecimal getWarnBps() {
            return warnBps;
        }

        public void setWarnBps(BigDecimal warnBps) {
            this.warnBps = warnBps;
        }

        public BigDecimal getCriticalBps() {
            return criticalBps;
        }

        public void setCriticalBps(BigDecimal criticalBps) {
            this.criticalBps = criticalBps;
        }
    }

    public static class StaleDataRule {
        private boolean enabled = true;
        private long warnSeconds = 10;
        private long criticalSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getWarnSeconds() {
            return warnSeconds;
        }

        public void setWarnSeconds(long warnSeconds) {
            this.warnSeconds = warnSeconds;
        }

        public long getCriticalSeconds() {
            return criticalSeconds;
        }

        public void setCriticalSeconds(long criticalSeconds) {
            this.criticalSeconds = criticalSeconds;
        }
    }

    public static class CrossedQuoteRule {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class VolatilitySurgeRule {
        private boolean enabled = false;
        private int lookbackSeconds = 60;
        private int baselineLookbackSeconds = 600;
        private BigDecimal baselineMultiplier = new BigDecimal("3.0");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getLookbackSeconds() {
            return lookbackSeconds;
        }

        public void setLookbackSeconds(int lookbackSeconds) {
            this.lookbackSeconds = lookbackSeconds;
        }

        public int getBaselineLookbackSeconds() {
            return baselineLookbackSeconds;
        }

        public void setBaselineLookbackSeconds(int baselineLookbackSeconds) {
            this.baselineLookbackSeconds = baselineLookbackSeconds;
        }

        public BigDecimal getBaselineMultiplier() {
            return baselineMultiplier;
        }

        public void setBaselineMultiplier(BigDecimal baselineMultiplier) {
            this.baselineMultiplier = baselineMultiplier;
        }
    }
}
