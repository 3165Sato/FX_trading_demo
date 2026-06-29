package com.example.fx.demo.backend.position.dto;

public record PositionOcoOrderRequest(
        PositionOcoOrderLegRequest tp,
        PositionOcoOrderLegRequest sl
) {
}
