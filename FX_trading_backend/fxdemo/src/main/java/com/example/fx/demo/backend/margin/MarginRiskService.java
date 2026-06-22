package com.example.fx.demo.backend.margin;

import com.example.fx.demo.backend.account.AccountSummaryService;
import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.position.PositionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class MarginRiskService {

    private final AccountSummaryService accountSummaryService;
    private final MarginProperties marginProperties;
    private final PositionService positionService;

    public MarginRiskService(
            AccountSummaryService accountSummaryService,
            MarginProperties marginProperties,
            PositionService positionService
    ) {
        this.accountSummaryService = accountSummaryService;
        this.marginProperties = marginProperties;
        this.positionService = positionService;
    }

    public void assertSufficientMargin(
            String currencyPair,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal executionPrice
    ) {
        if (!marginProperties.getOrder().isMarginCheckEnabled()) {
            return;
        }

        AccountSummaryResponse summary = accountSummaryService.getDefaultAccountSummary();
        BigDecimal equity = summary.equity();
        if (equity == null) {
            return;
        }

        BigDecimal projectedUsedMargin = positionService.calculateProjectedUsedMargin(
                currencyPair,
                side,
                quantity,
                executionPrice
        );
        if (projectedUsedMargin == null) {
            return;
        }

        if (equity.subtract(projectedUsedMargin).compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_MARGIN: 証拠金不足で発注できません"
            );
        }
    }
}
