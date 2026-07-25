package com.example.fx.demo.backend.cash.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashTransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
}
