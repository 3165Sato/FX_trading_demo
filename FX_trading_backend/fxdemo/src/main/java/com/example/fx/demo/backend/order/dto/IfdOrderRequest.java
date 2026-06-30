package com.example.fx.demo.backend.order.dto;

import com.example.fx.demo.backend.position.dto.PositionExitOrderRequest;

public record IfdOrderRequest(
        PendingOrderRequest entry,
        PositionExitOrderRequest exit
) {
}
