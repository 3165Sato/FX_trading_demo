package com.example.fx.demo.backend.market.news;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "demofx.news")
public class NewsEventProperties {

    private boolean enabled = true;
    private Defaults defaults = new Defaults();
    private Limits limits = new Limits();
    private Auto auto = new Auto();
    private List<String> headlines = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Defaults getDefaults() {
        return defaults;
    }

    public void setDefaults(Defaults defaults) {
        this.defaults = defaults;
    }

    public Limits getLimits() {
        return limits;
    }

    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    public Auto getAuto() {
        return auto;
    }

    public void setAuto(Auto auto) {
        this.auto = auto;
    }

    public List<String> getHeadlines() {
        return headlines;
    }

    public void setHeadlines(List<String> headlines) {
        this.headlines = headlines;
    }

    public static class Defaults {

        private BigDecimal magnitudeBps = new BigDecimal("60");
        private int durationSeconds = 30;
        private double volatilityMultiplier = 5.0;
        private double spreadMultiplier = 4.0;

        public BigDecimal getMagnitudeBps() {
            return magnitudeBps;
        }

        public void setMagnitudeBps(BigDecimal magnitudeBps) {
            this.magnitudeBps = magnitudeBps;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }

        public void setDurationSeconds(int durationSeconds) {
            this.durationSeconds = durationSeconds;
        }

        public double getVolatilityMultiplier() {
            return volatilityMultiplier;
        }

        public void setVolatilityMultiplier(double volatilityMultiplier) {
            this.volatilityMultiplier = volatilityMultiplier;
        }

        public double getSpreadMultiplier() {
            return spreadMultiplier;
        }

        public void setSpreadMultiplier(double spreadMultiplier) {
            this.spreadMultiplier = spreadMultiplier;
        }
    }

    public static class Limits {

        private BigDecimal maxMagnitudeBps = new BigDecimal("500");
        private int maxDurationSeconds = 300;

        public BigDecimal getMaxMagnitudeBps() {
            return maxMagnitudeBps;
        }

        public void setMaxMagnitudeBps(BigDecimal maxMagnitudeBps) {
            this.maxMagnitudeBps = maxMagnitudeBps;
        }

        public int getMaxDurationSeconds() {
            return maxDurationSeconds;
        }

        public void setMaxDurationSeconds(int maxDurationSeconds) {
            this.maxDurationSeconds = maxDurationSeconds;
        }
    }

    public static class Auto {

        private boolean enabled = false;
        private double probabilityPerMinute = 0.2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getProbabilityPerMinute() {
            return probabilityPerMinute;
        }

        public void setProbabilityPerMinute(double probabilityPerMinute) {
            this.probabilityPerMinute = probabilityPerMinute;
        }
    }
}
