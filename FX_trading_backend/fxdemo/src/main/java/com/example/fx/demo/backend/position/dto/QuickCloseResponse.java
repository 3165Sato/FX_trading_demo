package com.example.fx.demo.backend.position.dto;

import com.example.fx.demo.backend.common.enums.QuickCloseScope;

import java.util.List;

public record QuickCloseResponse(
        QuickCloseScope scope,
        String currencyPair,
        int targetCount,
        int successCount,
        int failureCount,
        List<PositionCloseResponse> successes,
        List<QuickCloseFailureResponse> failures
) {
}
