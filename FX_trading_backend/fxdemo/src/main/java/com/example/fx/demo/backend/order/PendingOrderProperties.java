package com.example.fx.demo.backend.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "demofx.pending-orders")
public class PendingOrderProperties {

    private boolean enabled = true;
    private int evaluationIntervalSeconds = 1;

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
}
