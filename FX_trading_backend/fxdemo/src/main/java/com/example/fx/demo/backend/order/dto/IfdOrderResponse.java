package com.example.fx.demo.backend.order.dto;

public record IfdOrderResponse(
        PendingOrderResponse entry,
        PendingOrderResponse exit
) {
}
