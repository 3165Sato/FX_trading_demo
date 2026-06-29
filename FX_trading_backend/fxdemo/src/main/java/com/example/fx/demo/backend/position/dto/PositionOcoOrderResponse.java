package com.example.fx.demo.backend.position.dto;

import java.util.List;

public record PositionOcoOrderResponse(
        String ocoGroupId,
        List<PositionExitOrderResponse> orders
) {
}
