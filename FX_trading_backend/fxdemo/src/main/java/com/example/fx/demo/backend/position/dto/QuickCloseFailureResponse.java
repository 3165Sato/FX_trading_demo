package com.example.fx.demo.backend.position.dto;

public record QuickCloseFailureResponse(
        Long positionId,
        String currencyPair,
        String reason
) {
}
