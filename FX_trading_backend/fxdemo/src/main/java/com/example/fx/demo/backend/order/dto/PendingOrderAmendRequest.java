package com.example.fx.demo.backend.order.dto;

import java.math.BigDecimal;

public class PendingOrderAmendRequest {

    private BigDecimal quantity;
    private BigDecimal triggerPrice;
    private boolean quantitySpecified;
    private boolean triggerPriceSpecified;

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
        this.quantitySpecified = true;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
        this.triggerPriceSpecified = true;
    }

    public boolean isQuantitySpecified() {
        return quantitySpecified;
    }

    public boolean isTriggerPriceSpecified() {
        return triggerPriceSpecified;
    }
}
