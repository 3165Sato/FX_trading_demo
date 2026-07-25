package com.example.fx.demo.backend.cash.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "demofx.cash")
public class CashProperties {

    private BigDecimal maxDepositPerRequest = new BigDecimal("100000000");

    public BigDecimal getMaxDepositPerRequest() {
        return maxDepositPerRequest;
    }

    public void setMaxDepositPerRequest(BigDecimal maxDepositPerRequest) {
        this.maxDepositPerRequest = maxDepositPerRequest;
    }
}
