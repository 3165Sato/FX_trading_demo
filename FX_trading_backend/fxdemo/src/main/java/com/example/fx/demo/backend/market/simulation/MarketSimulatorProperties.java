package com.example.fx.demo.backend.market.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "demofx.simulator")
public class MarketSimulatorProperties {

    private SimulatorTuning defaults = new SimulatorTuning();
    private List<PairTuning> pairs = new ArrayList<>();

    public SimulatorTuning getDefault() {
        return defaults;
    }

    public void setDefault(SimulatorTuning defaults) {
        this.defaults = defaults;
    }

    public List<PairTuning> getPairs() {
        return pairs;
    }

    public void setPairs(List<PairTuning> pairs) {
        this.pairs = pairs;
    }

    public SimulatorTuning tuningFor(String symbol) {
        return pairs.stream()
                .filter(pair -> symbol.equals(pair.getSymbol()))
                .findFirst()
                .map(pair -> pair.withFallback(defaults))
                .orElse(defaults);
    }

    public Optional<BigDecimal> configuredBasePrice(String symbol) {
        return pairs.stream()
                .filter(pair -> symbol.equals(pair.getSymbol()))
                .map(PairTuning::getBasePrice)
                .filter(basePrice -> basePrice != null)
                .findFirst();
    }

    public static class SimulatorTuning {

        private Double volatilityBps = 1.5;
        private Double reversionStrength = 0.01;
        private Double maxDeviationBps = 300.0;

        public Double getVolatilityBps() {
            return volatilityBps;
        }

        public void setVolatilityBps(Double volatilityBps) {
            this.volatilityBps = volatilityBps;
        }

        public Double getReversionStrength() {
            return reversionStrength;
        }

        public void setReversionStrength(Double reversionStrength) {
            this.reversionStrength = reversionStrength;
        }

        public Double getMaxDeviationBps() {
            return maxDeviationBps;
        }

        public void setMaxDeviationBps(Double maxDeviationBps) {
            this.maxDeviationBps = maxDeviationBps;
        }
    }

    public static class PairTuning extends SimulatorTuning {

        private String symbol;
        private BigDecimal basePrice;

        public PairTuning() {
            setVolatilityBps(null);
            setReversionStrength(null);
            setMaxDeviationBps(null);
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public BigDecimal getBasePrice() {
            return basePrice;
        }

        public void setBasePrice(BigDecimal basePrice) {
            this.basePrice = basePrice;
        }

        private SimulatorTuning withFallback(SimulatorTuning defaults) {
            SimulatorTuning tuning = new SimulatorTuning();
            tuning.setVolatilityBps(getVolatilityBps() == null ? defaults.getVolatilityBps() : getVolatilityBps());
            tuning.setReversionStrength(
                    getReversionStrength() == null ? defaults.getReversionStrength() : getReversionStrength()
            );
            tuning.setMaxDeviationBps(getMaxDeviationBps() == null ? defaults.getMaxDeviationBps() : getMaxDeviationBps());
            return tuning;
        }
    }
}
