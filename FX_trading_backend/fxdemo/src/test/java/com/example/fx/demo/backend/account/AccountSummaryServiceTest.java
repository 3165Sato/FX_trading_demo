package com.example.fx.demo.backend.account;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.account.service.AccountSummaryService;
import com.example.fx.demo.backend.margin.config.MarginProperties;
import com.example.fx.demo.backend.margin.service.CurrencyConverter;
import com.example.fx.demo.backend.market.rate.MarketRateRepository;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import com.example.fx.demo.backend.position.service.PositionService;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountSummaryServiceTest {

    private Account account;
    private PositionService positionService;
    private AccountSummaryService service;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER);
        account.setBalance(new BigDecimal("1000000"));
        account.setRealizedPnl(BigDecimal.ZERO);

        AccountRepository accountRepository = mock(AccountRepository.class);
        MarketRateRepository marketRateRepository = mock(MarketRateRepository.class);
        positionService = mock(PositionService.class);
        when(accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(account));
        when(marketRateRepository.findByCurrencyPair_EnabledTrue()).thenReturn(List.of());

        service = new AccountSummaryService(
                accountRepository,
                new MarginProperties(),
                marketRateRepository,
                positionService,
                new CurrencyConverter()
        );
    }

    @Test
    void capsWithdrawableAtFreeMarginWhenPositionHasUnrealizedLoss() {
        when(positionService.getPositions(null)).thenReturn(List.of(position("-20000", "62000")));

        AccountSummaryResponse summary = service.getDefaultAccountSummary();

        assertThat(summary.equity()).isEqualByComparingTo(new BigDecimal("980000"));
        assertThat(summary.freeMargin()).isEqualByComparingTo(new BigDecimal("918000"));
        assertThat(summary.withdrawable()).isEqualByComparingTo(new BigDecimal("918000"));
    }

    @Test
    void capsWithdrawableAtBalanceWhenFreeMarginIncludesUnrealizedProfit() {
        when(positionService.getPositions(null)).thenReturn(List.of(position("200000", "0")));

        AccountSummaryResponse summary = service.getDefaultAccountSummary();

        assertThat(summary.freeMargin()).isEqualByComparingTo(new BigDecimal("1200000"));
        assertThat(summary.withdrawable()).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    void allowsFullBalanceWhenThereAreNoPositions() {
        when(positionService.getPositions(null)).thenReturn(List.of());

        AccountSummaryResponse summary = service.getDefaultAccountSummary();

        assertThat(summary.withdrawable()).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    void floorsWithdrawableAtZeroWhenFreeMarginIsNegative() {
        when(positionService.getPositions(null)).thenReturn(List.of(position("-950000", "62000")));

        AccountSummaryResponse summary = service.getDefaultAccountSummary();

        assertThat(summary.freeMargin()).isEqualByComparingTo(new BigDecimal("-12000"));
        assertThat(summary.withdrawable()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void leavesOneHundredPercentMarginRatioAfterWithdrawingTheExactLimit() {
        account.setBalance(new BigDecimal("82000"));
        when(positionService.getPositions(null)).thenReturn(List.of(position("-20000", "62000")));

        AccountSummaryResponse summary = service.getDefaultAccountSummary();

        assertThat(summary.equity()).isEqualByComparingTo(new BigDecimal("62000"));
        assertThat(summary.marginRatio()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void returnsNullWithdrawableWhenEquityCannotBeCalculated() {
        when(positionService.getPositions(null)).thenReturn(List.of(position(null, "62000")));

        AccountSummaryResponse summary = service.getDefaultAccountSummary();

        assertThat(summary.equity()).isNull();
        assertThat(summary.freeMargin()).isNull();
        assertThat(summary.withdrawable()).isNull();
    }

    private PositionResponse position(String unrealizedPnl, String requiredMargin) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 14, 12, 0);
        return new PositionResponse(
                1L,
                "USD/JPY",
                "LONG",
                new BigDecimal("10000"),
                new BigDecimal("155.000"),
                "JPY",
                new BigDecimal("153.000"),
                decimal(unrealizedPnl),
                BigDecimal.ZERO,
                now,
                decimal(requiredMargin),
                now,
                List.of()
        );
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
