package com.example.fx.demo.backend.order.dto;

import com.example.fx.demo.backend.position.dto.PositionOcoOrderRequest;

public record IfoOrderRequest(
        PendingOrderRequest entry,
        PositionOcoOrderRequest oco
) {
}
