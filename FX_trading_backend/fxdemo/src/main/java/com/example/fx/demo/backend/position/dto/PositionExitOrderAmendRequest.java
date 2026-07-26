package com.example.fx.demo.backend.position.dto;

import java.math.BigDecimal;

public class PositionExitOrderAmendRequest {

    private BigDecimal triggerPrice;
    private boolean triggerPriceSpecified;
    private boolean quantitySpecified;

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
        this.triggerPriceSpecified = true;
    }

    public void setQuantity(BigDecimal ignored) {
        // Presence alone is retained so the service can reject unsupported TP/SL quantity amendments explicitly.
        this.quantitySpecified = true;
    }

    public boolean isTriggerPriceSpecified() {
        return triggerPriceSpecified;
    }

    public boolean isQuantitySpecified() {
        return quantitySpecified;
    }
}
