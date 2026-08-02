package com.example.fx.demo.backend.trade;

import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.account.service.AccountSummaryService;
import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.market.pair.CurrencyPairRepository;
import com.example.fx.demo.backend.market.rate.MarketRateRepository;
import com.example.fx.demo.backend.margin.service.MarginRiskService;
import com.example.fx.demo.backend.order.repository.FxOrderRepository;
import com.example.fx.demo.backend.position.service.PositionService;
import com.example.fx.demo.backend.position.dto.PositionCloseResponse;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import com.example.fx.demo.backend.trade.dto.OrderResultResponse;
import com.example.fx.demo.backend.trade.dto.OrderSummaryResponse;
import com.example.fx.demo.backend.trade.dto.TradeSummaryResponse;
import com.example.fx.demo.backend.trade.repository.TradeRepository;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import com.example.fx.demo.backend.trade.service.TradeExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeExecutionServiceLossCutTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CurrencyPairRepository currencyPairRepository;

    @Mock
    private MarketRateRepository marketRateRepository;

    @Mock
    private AccountSummaryService accountSummaryService;

    @Mock
    private FxOrderRepository fxOrderRepository;

    @Mock
    private MarginRiskService marginRiskService;

    @Mock
    private PositionService positionService;

    @Mock
    private TradeRepository tradeRepository;

    private TradeExecutionService service;

    @BeforeEach
    void setUp() {
        service = new TradeExecutionService(
                accountRepository,
                currencyPairRepository,
                marketRateRepository,
                accountSummaryService,
                fxOrderRepository,
                marginRiskService,
                positionService,
                tradeRepository,
                new AccountTradeLockService()
        );
    }

    @Test
    void skipsLossCutWhenMarginRatioRecoveredBeforeLiquidation() {
        when(accountSummaryService.getDefaultAccountSummary()).thenReturn(summary("80"));

        List<OrderResultResponse> results = service.liquidateAllPositionsIfMarginRatioAtOrBelow(new BigDecimal("50"));

        assertThat(results).isEmpty();
        verify(positionService, never()).getPositions(null);
    }

    @Test
    void closesAllOpenPositionsWithLossCutSourceWhenMarginRatioBreachesThreshold() {
        when(accountSummaryService.getDefaultAccountSummary()).thenReturn(summary("40"));
        when(positionService.getPositions(null)).thenReturn(List.of(position(1L, "LONG"), position(2L, "SHORT")));
        when(positionService.closePositionForLockedAccount(1L, OrderSource.LOSS_CUT))
                .thenReturn(closeResponse(1L));
        when(positionService.closePositionForLockedAccount(2L, OrderSource.LOSS_CUT))
                .thenReturn(closeResponse(2L));

        List<OrderResultResponse> results = service.liquidateAllPositionsIfMarginRatioAtOrBelow(new BigDecimal("50"));

        assertThat(results).hasSize(2);
        verify(positionService).closePositionForLockedAccount(1L, OrderSource.LOSS_CUT);
        verify(positionService).closePositionForLockedAccount(2L, OrderSource.LOSS_CUT);
    }

    @Test
    void continuesClosingOtherPositionsWhenOnePositionCannotBeClosed() {
        when(accountSummaryService.getDefaultAccountSummary()).thenReturn(summary("40"));
        when(positionService.getPositions(null)).thenReturn(List.of(position(1L, "LONG"), position(2L, "SHORT")));
        when(positionService.closePositionForLockedAccount(1L, OrderSource.LOSS_CUT))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Latest market rate is not available"));
        when(positionService.closePositionForLockedAccount(2L, OrderSource.LOSS_CUT))
                .thenReturn(closeResponse(2L));

        List<OrderResultResponse> results = service.liquidateAllPositionsIfMarginRatioAtOrBelow(new BigDecimal("50"));

        assertThat(results).hasSize(1);
        verify(positionService).closePositionForLockedAccount(1L, OrderSource.LOSS_CUT);
        verify(positionService).closePositionForLockedAccount(2L, OrderSource.LOSS_CUT);
    }

    private AccountSummaryResponse summary(String marginRatio) {
        return new AccountSummaryResponse(
                "DEMO-ACCOUNT",
                "JPY",
                new BigDecimal("1000000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1000000"),
                new BigDecimal("100000"),
                new BigDecimal("900000"),
                new BigDecimal("900000"),
                new BigDecimal(marginRatio),
                new BigDecimal("50"),
                "ACTIVE"
        );
    }

    private PositionResponse position(Long id, String side) {
        return new PositionResponse(
                id,
                "USD/JPY",
                side,
                new BigDecimal("1000"),
                new BigDecimal("155.000"),
                "JPY",
                new BigDecimal("154.900"),
                new BigDecimal("-100"),
                BigDecimal.ZERO,
                LocalDateTime.of(2026, 6, 26, 12, 0),
                new BigDecimal("6200"),
                LocalDateTime.of(2026, 6, 26, 11, 0),
                List.of()
        );
    }

    private PositionCloseResponse closeResponse(Long positionId) {
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 12, 1);
        OrderResultResponse execution = new OrderResultResponse(
                new OrderSummaryResponse(
                        positionId,
                        "USD/JPY",
                        "SELL",
                        "MARKET",
                        new BigDecimal("1000"),
                        "EXECUTED",
                        OrderSource.LOSS_CUT.name(),
                        now
                ),
                new TradeSummaryResponse(
                        positionId,
                        positionId,
                        "USD/JPY",
                        "SELL",
                        new BigDecimal("1000"),
                        new BigDecimal("154.900"),
                        now,
                        "CLOSE",
                        positionId,
                        new BigDecimal("-100"),
                        OrderSource.LOSS_CUT.name()
                )
        );
        return new PositionCloseResponse(
                positionId,
                "USD/JPY",
                "LONG",
                new BigDecimal("1000"),
                new BigDecimal("154.900"),
                new BigDecimal("-100"),
                BigDecimal.ZERO,
                "JPY",
                now,
                execution
        );
    }
}
