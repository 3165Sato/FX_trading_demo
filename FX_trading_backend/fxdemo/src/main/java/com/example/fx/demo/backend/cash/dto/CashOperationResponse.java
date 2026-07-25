package com.example.fx.demo.backend.cash.dto;

import java.math.BigDecimal;

public record CashOperationResponse(
        CashTransactionResponse transaction,
        BigDecimal balanceAfter
) {
}
