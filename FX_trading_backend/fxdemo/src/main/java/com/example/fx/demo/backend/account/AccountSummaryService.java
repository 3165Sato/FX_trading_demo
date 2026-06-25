package com.example.fx.demo.backend.account;

import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.market.MarketRate;
import com.example.fx.demo.backend.market.MarketRateRepository;
import com.example.fx.demo.backend.margin.CurrencyConverter;
import com.example.fx.demo.backend.margin.MarginProperties;
import com.example.fx.demo.backend.position.PositionService;
import com.example.fx.demo.backend.position.dto.PnlSummaryResponse;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import com.example.fx.demo.backend.trade.DemoTradingAccountInitializer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccountSummaryService {

    private static final String BASE_CURRENCY = "JPY";
    private final AccountRepository accountRepository;
    private final MarginProperties marginProperties;
    private final MarketRateRepository marketRateRepository;
    private final PositionService positionService;
    private final CurrencyConverter currencyConverter;

    public AccountSummaryService(
            AccountRepository accountRepository,
            MarginProperties marginProperties,
            MarketRateRepository marketRateRepository,
            PositionService positionService,
            CurrencyConverter currencyConverter
    ) {
        this.accountRepository = accountRepository;
        this.marginProperties = marginProperties;
        this.marketRateRepository = marketRateRepository;
        this.positionService = positionService;
        this.currencyConverter = currencyConverter;
    }

    @Transactional(readOnly = true)
    public AccountSummaryResponse getDefaultAccountSummary() {
        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));

        Map<String, BigDecimal> midRates = loadMidRates();
        List<PositionResponse> positions = positionService.getPositions(null);
        BigDecimal realizedPnl = account.getRealizedPnl() == null
                ? BigDecimal.ZERO
                : scaleJpy(account.getRealizedPnl());
        BigDecimal balance = baseBalance(account);
        BigDecimal unrealizedPnl = sumUnrealizedPnl(positions, midRates);
        BigDecimal usedMargin = sumUsedMargin(positions);
        BigDecimal equity = addNullable(balance, unrealizedPnl);
        BigDecimal freeMargin = subtractNullable(equity, usedMargin);
        BigDecimal marginRatio = calculateMarginRatio(equity, usedMargin);
        String status = statusOf(marginRatio);

        return new AccountSummaryResponse(
                account.getAccountNumber(),
                BASE_CURRENCY,
                scaleJpy(balance),
                realizedPnl,
                unrealizedPnl,
                scaleJpy(equity),
                usedMargin,
                scaleJpy(freeMargin),
                marginRatio,
                marginProperties.getLossCut().getThresholdPercent(),
                status
        );
    }

    private BigDecimal sumUnrealizedPnl(List<PositionResponse> positions, Map<String, BigDecimal> midRates) {
        BigDecimal total = BigDecimal.ZERO;
        for (PositionResponse position : positions) {
            if (position.unrealizedPnl() == null) {
                return null;
            }
            BigDecimal converted = currencyConverter.toJpy(position.unrealizedPnl(), position.quoteCurrency(), midRates);
            if (converted == null) {
                return null;
            }
            total = total.add(converted);
        }
        return scaleJpy(total);
    }

    private BigDecimal sumUsedMargin(List<PositionResponse> positions) {
        BigDecimal total = BigDecimal.ZERO;
        for (PositionResponse position : positions) {
            if (position.requiredMargin() == null) {
                return null;
            }
            total = total.add(position.requiredMargin());
        }
        return scaleJpy(total);
    }

    private BigDecimal calculateMarginRatio(BigDecimal equity, BigDecimal usedMargin) {
        if (equity == null || usedMargin == null || usedMargin.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return equity.multiply(new BigDecimal("100")).divide(usedMargin, 2, RoundingMode.HALF_UP);
    }

    private String statusOf(BigDecimal marginRatio) {
        if (marginRatio == null) {
            return "SAFE";
        }
        if (marginRatio.compareTo(marginProperties.getLossCut().getThresholdPercent()) <= 0) {
            return "DANGER";
        }
        if (marginRatio.compareTo(marginProperties.getLossCut().getWarningPercent()) <= 0) {
            return "WARNING";
        }
        return "SAFE";
    }

    private BigDecimal baseBalance(Account account) {
        return account.getBalance() == null ? BigDecimal.ZERO : scaleJpy(account.getBalance());
    }

    private BigDecimal addNullable(BigDecimal first, BigDecimal second) {
        return first == null || second == null ? null : first.add(second);
    }

    private BigDecimal subtractNullable(BigDecimal first, BigDecimal second) {
        return first == null || second == null ? null : first.subtract(second);
    }

    private BigDecimal scaleJpy(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> loadMidRates() {
        return marketRateRepository.findByCurrencyPair_EnabledTrue().stream()
                .collect(Collectors.toMap(
                        rate -> rate.getCurrencyPair().getSymbol(),
                        MarketRate::getMidPrice,
                        (first, ignored) -> first
                ));
    }
}
