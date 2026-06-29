package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.account.Account;
import com.example.fx.demo.backend.account.AccountRepository;
import com.example.fx.demo.backend.common.enums.ExitOrderType;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.PositionSide;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import com.example.fx.demo.backend.common.enums.TriggerOrderPurpose;
import com.example.fx.demo.backend.common.enums.TriggerOrderStatus;
import com.example.fx.demo.backend.market.CurrencyPair;
import com.example.fx.demo.backend.market.CurrencyPairRepository;
import com.example.fx.demo.backend.market.MarketRate;
import com.example.fx.demo.backend.market.MarketRateRepository;
import com.example.fx.demo.backend.position.Position;
import com.example.fx.demo.backend.position.PositionRepository;
import com.example.fx.demo.backend.position.PositionService;
import com.example.fx.demo.backend.position.dto.PositionCloseResponse;
import com.example.fx.demo.backend.position.dto.PositionExitOrderRequest;
import com.example.fx.demo.backend.position.dto.PositionOcoOrderLegRequest;
import com.example.fx.demo.backend.position.dto.PositionOcoOrderRequest;
import com.example.fx.demo.backend.trade.AccountTradeLockService;
import com.example.fx.demo.backend.trade.TradeExecutionService;
import com.example.fx.demo.backend.trade.dto.OrderResultResponse;
import com.example.fx.demo.backend.trade.dto.OrderSummaryResponse;
import com.example.fx.demo.backend.trade.dto.TradeSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TriggerOrderServiceExitOrderTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CurrencyPairRepository currencyPairRepository;

    @Mock
    private MarketRateRepository marketRateRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private PositionService positionService;

    @Mock
    private TradeExecutionService tradeExecutionService;

    @Mock
    private TriggerOrderRepository triggerOrderRepository;

    private TriggerOrderService service;

    @BeforeEach
    void setUp() {
        service = new TriggerOrderService(
                accountRepository,
                new AccountTradeLockService(),
                currencyPairRepository,
                marketRateRepository,
                positionRepository,
                positionService,
                tradeExecutionService,
                triggerOrderRepository
        );
    }

    @Test
    void createsLongTakeProfitExitOrderAsSellLimitForTargetPosition() {
        mockDefaultAccount();
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        mockMarketRate("155.120", "155.123");
        when(triggerOrderRepository.existsByTargetPositionIdAndExitTypeAndStatusIn(
                1L,
                ExitOrderType.TP,
                List.of(TriggerOrderStatus.PENDING, TriggerOrderStatus.WAITING)
        )).thenReturn(false);
        when(triggerOrderRepository.save(any(TriggerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.placeExitOrder(1L, new PositionExitOrderRequest(ExitOrderType.TP, new BigDecimal("156.000")));

        ArgumentCaptor<TriggerOrder> captor = ArgumentCaptor.forClass(TriggerOrder.class);
        verify(triggerOrderRepository).save(captor.capture());
        TriggerOrder saved = captor.getValue();
        assertThat(saved.getPurpose()).isEqualTo(TriggerOrderPurpose.EXIT);
        assertThat(saved.getExitType()).isEqualTo(ExitOrderType.TP);
        assertThat(saved.getTargetPositionId()).isEqualTo(1L);
        assertThat(saved.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(saved.getOrderType()).isEqualTo(OrderType.LIMIT);
        assertThat(saved.getTriggerPrice()).isEqualByComparingTo(new BigDecimal("156.000"));
    }

    @Test
    void rejectsLongTakeProfitBelowCurrentBid() {
        mockDefaultAccount();
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        mockMarketRate("155.120", "155.123");

        assertThatThrownBy(() -> service.placeExitOrder(
                1L,
                new PositionExitOrderRequest(ExitOrderType.TP, new BigDecimal("154.000"))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void triggersTakeProfitAtSpecifiedPrice() {
        TriggerOrder order = new TriggerOrder();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setPurpose(TriggerOrderPurpose.EXIT);
        order.setExitType(ExitOrderType.TP);
        order.setTargetPositionId(1L);
        order.setCurrencyPair("USD/JPY");
        order.setTriggerPrice(new BigDecimal("156.000"));
        order.setStatus(TriggerOrderStatus.PENDING);
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        mockMarketRate("156.100", "156.103");
        when(positionService.closePositionForLockedAccount(1L, OrderSource.TRIGGER, new BigDecimal("156.000")))
                .thenReturn(closeResponse());

        service.evaluatePendingOrder(10L);

        verify(positionService).closePositionForLockedAccount(1L, OrderSource.TRIGGER, new BigDecimal("156.000"));
        assertThat(order.getStatus()).isEqualTo(TriggerOrderStatus.TRIGGERED);
        assertThat(order.getResultingOrderId()).isEqualTo(99L);
    }

    @Test
    void createsOcoPairWithSameGroupId() {
        mockDefaultAccount();
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        mockMarketRate("155.120", "155.123");
        when(triggerOrderRepository.existsByTargetPositionIdAndExitTypeAndStatusIn(
                1L,
                ExitOrderType.TP,
                List.of(TriggerOrderStatus.PENDING, TriggerOrderStatus.WAITING)
        )).thenReturn(false);
        when(triggerOrderRepository.existsByTargetPositionIdAndExitTypeAndStatusIn(
                1L,
                ExitOrderType.SL,
                List.of(TriggerOrderStatus.PENDING, TriggerOrderStatus.WAITING)
        )).thenReturn(false);
        when(triggerOrderRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.placeOcoOrder(
                1L,
                new PositionOcoOrderRequest(
                        new PositionOcoOrderLegRequest(new BigDecimal("156.000")),
                        new PositionOcoOrderLegRequest(new BigDecimal("154.000"))
                )
        );

        assertThat(response.ocoGroupId()).isNotBlank();
        assertThat(response.orders()).hasSize(2);
        assertThat(response.orders()).allSatisfy(order -> assertThat(order.ocoGroupId()).isEqualTo(response.ocoGroupId()));
    }

    @Test
    void cancelsOcoSiblingWhenOneSideTriggers() {
        TriggerOrder takeProfit = new TriggerOrder();
        ReflectionTestUtils.setField(takeProfit, "id", 10L);
        takeProfit.setPurpose(TriggerOrderPurpose.EXIT);
        takeProfit.setExitType(ExitOrderType.TP);
        takeProfit.setTargetPositionId(1L);
        takeProfit.setCurrencyPair("USD/JPY");
        takeProfit.setTriggerPrice(new BigDecimal("156.000"));
        takeProfit.setStatus(TriggerOrderStatus.PENDING);
        takeProfit.setOcoGroupId("oco-1");
        TriggerOrder stopLoss = new TriggerOrder();
        ReflectionTestUtils.setField(stopLoss, "id", 11L);
        stopLoss.setPurpose(TriggerOrderPurpose.EXIT);
        stopLoss.setExitType(ExitOrderType.SL);
        stopLoss.setTargetPositionId(1L);
        stopLoss.setCurrencyPair("USD/JPY");
        stopLoss.setTriggerPrice(new BigDecimal("154.000"));
        stopLoss.setStatus(TriggerOrderStatus.PENDING);
        stopLoss.setOcoGroupId("oco-1");
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(takeProfit));
        when(triggerOrderRepository.findByOcoGroupIdOrderByCreatedAtAsc("oco-1")).thenReturn(List.of(takeProfit, stopLoss));
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        mockMarketRate("156.100", "156.103");
        when(positionService.closePositionForLockedAccount(1L, OrderSource.TRIGGER, new BigDecimal("156.000")))
                .thenReturn(closeResponse());

        service.evaluatePendingOrder(10L);

        assertThat(takeProfit.getStatus()).isEqualTo(TriggerOrderStatus.TRIGGERED);
        assertThat(stopLoss.getStatus()).isEqualTo(TriggerOrderStatus.CANCELLED);
    }

    private void mockDefaultAccount() {
        Account account = org.mockito.Mockito.mock(Account.class);
        when(account.getId()).thenReturn(100L);
        when(accountRepository.findByAccountNumber("DEMO-ACCOUNT-001")).thenReturn(Optional.of(account));
    }

    private void mockOpenPosition(PositionSide side) {
        Position position = org.mockito.Mockito.mock(Position.class);
        when(position.getId()).thenReturn(1L);
        when(position.getAccountId()).thenReturn(100L);
        when(position.getCurrencyPair()).thenReturn("USD/JPY");
        when(position.getSide()).thenReturn(side);
        when(position.getQuantity()).thenReturn(new BigDecimal("1000"));
        when(position.getStatus()).thenReturn(PositionStatus.OPEN);
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));
    }

    private void mockCurrencyPair() {
        CurrencyPair currencyPair = org.mockito.Mockito.mock(CurrencyPair.class);
        when(currencyPair.getSymbol()).thenReturn("USD/JPY");
        when(currencyPair.getEnabled()).thenReturn(true);
        when(currencyPair.getPriceScale()).thenReturn(3);
        when(currencyPair.getQuantityScale()).thenReturn(0);
        when(currencyPairRepository.findBySymbol("USD/JPY")).thenReturn(Optional.of(currencyPair));
    }

    private void mockMarketRate(String bid, String ask) {
        MarketRate marketRate = org.mockito.Mockito.mock(MarketRate.class);
        when(marketRate.getBid()).thenReturn(new BigDecimal(bid));
        when(marketRate.getAsk()).thenReturn(new BigDecimal(ask));
        when(marketRateRepository.findByCurrencyPair(any(CurrencyPair.class))).thenReturn(Optional.of(marketRate));
    }

    private PositionCloseResponse closeResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 29, 12, 0);
        OrderResultResponse execution = new OrderResultResponse(
                new OrderSummaryResponse(
                        99L,
                        "USD/JPY",
                        "SELL",
                        "MARKET",
                        new BigDecimal("1000"),
                        "EXECUTED",
                        OrderSource.TRIGGER.name(),
                        now
                ),
                new TradeSummaryResponse(
                        100L,
                        99L,
                        "USD/JPY",
                        "SELL",
                        new BigDecimal("1000"),
                        new BigDecimal("156.000"),
                        now,
                        "CLOSE",
                        1L,
                        new BigDecimal("1000")
                )
        );
        return new PositionCloseResponse(
                1L,
                "USD/JPY",
                "LONG",
                new BigDecimal("1000"),
                new BigDecimal("156.000"),
                new BigDecimal("1000"),
                "JPY",
                now,
                execution
        );
    }
}
