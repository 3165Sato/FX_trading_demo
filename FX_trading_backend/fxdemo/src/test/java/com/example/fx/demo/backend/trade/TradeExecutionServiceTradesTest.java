package com.example.fx.demo.backend.trade;

import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.account.service.AccountSummaryService;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.common.enums.TradeKind;
import com.example.fx.demo.backend.margin.service.MarginRiskService;
import com.example.fx.demo.backend.market.pair.CurrencyPairRepository;
import com.example.fx.demo.backend.market.rate.MarketRateRepository;
import com.example.fx.demo.backend.order.domain.FxOrder;
import com.example.fx.demo.backend.order.repository.FxOrderRepository;
import com.example.fx.demo.backend.position.service.PositionService;
import com.example.fx.demo.backend.trade.domain.Trade;
import com.example.fx.demo.backend.trade.dto.TradeSummaryResponse;
import com.example.fx.demo.backend.trade.repository.TradeRepository;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import com.example.fx.demo.backend.trade.service.TradeExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeExecutionServiceTradesTest {

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
    void resolvesOrderSourcesForTradeHistoryAndFallsBackToManual() {
        List<Trade> trades = List.of(
                trade(1L, 11L),
                trade(2L, 12L),
                trade(3L, 13L),
                trade(4L, 14L),
                trade(5L, 15L)
        );
        when(tradeRepository.findAllByOrderByExecutedAtDesc(any(Pageable.class))).thenReturn(trades);
        when(fxOrderRepository.findAllById(List.of(11L, 12L, 13L, 14L, 15L))).thenReturn(List.of(
                order(11L, OrderSource.MANUAL),
                order(12L, OrderSource.LOSS_CUT),
                order(13L, OrderSource.TRIGGER),
                order(14L, OrderSource.QUICK_CLOSE)
        ));

        List<TradeSummaryResponse> result = service.getTrades(null, 50);

        assertThat(result).extracting(TradeSummaryResponse::source).containsExactly(
                "MANUAL",
                "LOSS_CUT",
                "TRIGGER",
                "QUICK_CLOSE",
                "MANUAL"
        );
    }

    @Test
    void treatsNullOrderSourceAsManual() {
        when(tradeRepository.findAllByOrderByExecutedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(trade(1L, 11L)));
        when(fxOrderRepository.findAllById(List.of(11L)))
                .thenReturn(List.of(order(11L, null)));

        List<TradeSummaryResponse> result = service.getTrades(null, 50);

        assertThat(result).singleElement().extracting(TradeSummaryResponse::source).isEqualTo("MANUAL");
    }

    private Trade trade(Long id, Long orderId) {
        Trade trade = new Trade();
        ReflectionTestUtils.setField(trade, "id", id);
        trade.setOrderId(orderId);
        trade.setCurrencyPair("USD/JPY");
        trade.setSide(OrderSide.SELL);
        trade.setQuantity(new BigDecimal("1000"));
        trade.setExecutionPrice(new BigDecimal("155.000"));
        trade.setExecutedAt(LocalDateTime.of(2026, 7, 31, 12, 0));
        trade.setTradeKind(TradeKind.CLOSE);
        trade.setPositionId(id);
        trade.setRealizedPnl(BigDecimal.ZERO);
        return trade;
    }

    private FxOrder order(Long id, OrderSource source) {
        FxOrder order = new FxOrder();
        ReflectionTestUtils.setField(order, "id", id);
        order.setSource(source);
        return order;
    }
}
