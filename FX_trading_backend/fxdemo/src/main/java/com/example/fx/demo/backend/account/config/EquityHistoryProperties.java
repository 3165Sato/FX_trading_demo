package com.example.fx.demo.backend.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "demofx.equity-history")
public class EquityHistoryProperties {

    private boolean enabled = true;
    private int intervalSeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public long intervalMillis() {
        return Math.max(1, intervalSeconds) * 1000L;
    }
}
