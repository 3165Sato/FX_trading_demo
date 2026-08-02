package com.example.fx.demo.backend.position.dto;

import com.example.fx.demo.backend.common.enums.QuickCloseScope;

public record QuickCloseRequest(
        QuickCloseScope scope,
        String currencyPair
) {
}
