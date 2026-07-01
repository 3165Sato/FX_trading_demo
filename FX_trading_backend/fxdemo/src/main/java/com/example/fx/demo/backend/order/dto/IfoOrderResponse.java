package com.example.fx.demo.backend.order.dto;

import java.util.List;

public record IfoOrderResponse(
        PendingOrderResponse entry,
        String ocoGroupId,
        List<PendingOrderResponse> exits
) {
}
