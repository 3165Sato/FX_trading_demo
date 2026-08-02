package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.order.domain.TriggerOrder;
import com.example.fx.demo.backend.order.repository.TriggerOrderRepository;
import com.example.fx.demo.backend.order.service.TriggerOrderService;
import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.common.enums.ExitOrderType;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.PositionSide;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import com.example.fx.demo.backend.common.enums.TriggerOrderPurpose;
import com.example.fx.demo.backend.common.enums.TriggerOrderStatus;
import com.example.fx.demo.backend.market.pair.CurrencyPair;
import com.example.fx.demo.backend.market.pair.CurrencyPairRepository;
import com.example.fx.demo.backend.market.rate.MarketRate;
import com.example.fx.demo.backend.market.rate.MarketRateRepository;
import com.example.fx.demo.backend.position.domain.Position;
import com.example.fx.demo.backend.position.repository.PositionRepository;
import com.example.fx.demo.backend.position.service.PositionService;
import com.example.fx.demo.backend.position.dto.PositionCloseResponse;
import com.example.fx.demo.backend.position.dto.PositionExitOrderRequest;
import com.example.fx.demo.backend.position.dto.PositionExitOrderAmendRequest;
import com.example.fx.demo.backend.position.dto.PositionOcoOrderLegRequest;
import com.example.fx.demo.backend.position.dto.PositionOcoOrderRequest;
import com.example.fx.demo.backend.order.dto.IfdOrderRequest;
import com.example.fx.demo.backend.order.dto.IfoOrderRequest;
import com.example.fx.demo.backend.order.dto.PendingOrderRequest;
import com.example.fx.demo.backend.order.dto.PendingOrderResponse;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import com.example.fx.demo.backend.trade.service.TradeExecutionService;
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
    void amendsStandaloneLongTakeProfitPrice() {
        mockDefaultAccount();
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        mockMarketRate("155.120", "155.123");
        TriggerOrder order = standaloneExitOrder();
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(triggerOrderRepository.save(any(TriggerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PositionExitOrderAmendRequest request = new PositionExitOrderAmendRequest();
        request.setTriggerPrice(new BigDecimal("156.1236"));

        var response = service.amendExitOrder(1L, 10L, request);

        assertThat(response.triggerPrice()).isEqualByComparingTo("156.124");
        assertThat(order.getQuantity()).isEqualByComparingTo("1000");
    }

    @Test
    void rejectsOcoExitOrderAmendment() {
        mockDefaultAccount();
        mockOpenPosition(PositionSide.LONG);
        TriggerOrder order = standaloneExitOrder();
        order.setOcoGroupId("oco-1");
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        PositionExitOrderAmendRequest request = new PositionExitOrderAmendRequest();
        request.setTriggerPrice(new BigDecimal("156.000"));

        assertThatThrownBy(() -> service.amendExitOrder(1L, 10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsExitOrderAmendmentWithInvalidDirectionWithoutChangingPrice() {
        mockDefaultAccount();
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        mockMarketRate("155.120", "155.123");
        TriggerOrder order = standaloneExitOrder();
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        PositionExitOrderAmendRequest request = new PositionExitOrderAmendRequest();
        request.setTriggerPrice(new BigDecimal("154.000"));

        assertThatThrownBy(() -> service.amendExitOrder(1L, 10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(order.getTriggerPrice()).isEqualByComparingTo("156.000");
    }

    @Test
    void rejectsExitOrderQuantityAmendment() {
        PositionExitOrderAmendRequest request = new PositionExitOrderAmendRequest();
        request.setTriggerPrice(new BigDecimal("156.000"));
        request.setQuantity(new BigDecimal("500"));

        assertThatThrownBy(() -> service.amendExitOrder(1L, 10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsWaitingExitOrderAmendment() {
        mockDefaultAccount();
        mockOpenPosition(PositionSide.LONG);
        TriggerOrder order = standaloneExitOrder();
        order.setStatus(TriggerOrderStatus.WAITING);
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        PositionExitOrderAmendRequest request = exitAmendRequest("156.000");

        assertResponseStatus(() -> service.amendExitOrder(1L, 10L, request), HttpStatus.CONFLICT);
    }

    @Test
    void rejectsMissingExitOrderAndPosition() {
        mockDefaultAccount();
        PositionExitOrderAmendRequest request = exitAmendRequest("156.000");
        assertResponseStatus(() -> service.amendExitOrder(1L, 999L, request), HttpStatus.NOT_FOUND);

        TriggerOrder order = standaloneExitOrder();
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        assertResponseStatus(() -> service.amendExitOrder(999L, 10L, request), HttpStatus.NOT_FOUND);
    }

    @Test
    void hidesExitOrderOwnedByAnotherAccount() {
        mockDefaultAccount();
        Position position = org.mockito.Mockito.mock(Position.class);
        when(position.getId()).thenReturn(1L);
        when(position.getAccountId()).thenReturn(200L);
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));
        TriggerOrder order = standaloneExitOrder();
        order.setAccountId(200L);
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertResponseStatus(
                () -> service.amendExitOrder(1L, 10L, exitAmendRequest("156.000")),
                HttpStatus.NOT_FOUND
        );
    }

    @Test
    void rejectsExitOrderForClosedPosition() {
        mockDefaultAccount();
        Position position = org.mockito.Mockito.mock(Position.class);
        when(position.getId()).thenReturn(1L);
        when(position.getAccountId()).thenReturn(100L);
        when(position.getStatus()).thenReturn(PositionStatus.CLOSED);
        when(positionRepository.findById(1L)).thenReturn(Optional.of(position));
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(standaloneExitOrder()));

        assertResponseStatus(
                () -> service.amendExitOrder(1L, 10L, exitAmendRequest("156.000")),
                HttpStatus.CONFLICT
        );
    }

    @Test
    void rejectsExitOrderWhenLatestRateIsUnavailable() {
        mockDefaultAccount();
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(standaloneExitOrder()));
        when(marketRateRepository.findByCurrencyPair(any(CurrencyPair.class))).thenReturn(Optional.empty());

        assertResponseStatus(
                () -> service.amendExitOrder(1L, 10L, exitAmendRequest("156.000")),
                HttpStatus.CONFLICT
        );
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

    @Test
    void createsIfdParentAndUnboundExitChild() {
        mockDefaultAccount();
        mockCurrencyPair();
        mockMarketRate("155.120", "155.123");
        when(triggerOrderRepository.save(any(TriggerOrder.class))).thenAnswer(new SaveTriggerOrderAnswer());

        var response = service.placeIfdOrder(new IfdOrderRequest(
                new PendingOrderRequest(
                        "USD/JPY",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("1000"),
                        new BigDecimal("154.500")
                ),
                new PositionExitOrderRequest(ExitOrderType.TP, new BigDecimal("156.000"))
        ));

        assertThat(response.entry().purpose()).isEqualTo("ENTRY");
        assertThat(response.exit().purpose()).isEqualTo("EXIT");
        assertThat(response.exit().parentOrderId()).isEqualTo(response.entry().id());
        assertThat(response.exit().targetPositionId()).isNull();
    }

    @Test
    void createsIfoParentAndUnboundOcoExitChildren() {
        mockDefaultAccount();
        mockCurrencyPair();
        mockMarketRate("155.120", "155.123");
        when(triggerOrderRepository.save(any(TriggerOrder.class))).thenAnswer(new SaveTriggerOrderAnswer());
        when(triggerOrderRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.placeIfoOrder(new IfoOrderRequest(
                new PendingOrderRequest(
                        "USD/JPY",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        new BigDecimal("1000"),
                        new BigDecimal("154.500")
                ),
                new PositionOcoOrderRequest(
                        new PositionOcoOrderLegRequest(new BigDecimal("156.000")),
                        new PositionOcoOrderLegRequest(new BigDecimal("154.000"))
                )
        ));

        assertThat(response.entry().purpose()).isEqualTo("ENTRY");
        assertThat(response.ocoGroupId()).isNotBlank();
        assertThat(response.exits()).hasSize(2);
        assertThat(response.exits()).allSatisfy(order -> {
            assertThat(order.purpose()).isEqualTo("EXIT");
            assertThat(order.parentOrderId()).isEqualTo(response.entry().id());
            assertThat(order.targetPositionId()).isNull();
            assertThat(order.ocoGroupId()).isEqualTo(response.ocoGroupId());
        });
        assertThat(response.exits().stream().map(PendingOrderResponse::exitType).toList())
                .containsExactlyInAnyOrder("TP", "SL");
    }

    @Test
    void bindsIfdExitChildWhenParentEntryTriggers() {
        TriggerOrder parent = new TriggerOrder();
        ReflectionTestUtils.setField(parent, "id", 20L);
        parent.setPurpose(TriggerOrderPurpose.ENTRY);
        parent.setCurrencyPair("USD/JPY");
        parent.setSide(OrderSide.BUY);
        parent.setOrderType(OrderType.LIMIT);
        parent.setQuantity(new BigDecimal("1000"));
        parent.setTriggerPrice(new BigDecimal("154.500"));
        parent.setStatus(TriggerOrderStatus.PENDING);
        TriggerOrder child = new TriggerOrder();
        ReflectionTestUtils.setField(child, "id", 21L);
        child.setPurpose(TriggerOrderPurpose.EXIT);
        child.setExitType(ExitOrderType.TP);
        child.setParentOrderId(20L);
        child.setCurrencyPair("USD/JPY");
        child.setSide(OrderSide.SELL);
        child.setOrderType(OrderType.LIMIT);
        child.setQuantity(new BigDecimal("1000"));
        child.setTriggerPrice(new BigDecimal("156.000"));
        child.setStatus(TriggerOrderStatus.PENDING);
        when(triggerOrderRepository.findById(20L)).thenReturn(Optional.of(parent));
        when(triggerOrderRepository.findByParentOrderIdAndStatusInOrderByCreatedAtAsc(
                20L,
                List.of(TriggerOrderStatus.PENDING, TriggerOrderStatus.WAITING)
        )).thenReturn(List.of(child));
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        mockMarketRate("154.497", "154.500");
        when(tradeExecutionService.executeTriggeredOrder(
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("1000"),
                OrderType.LIMIT
        )).thenReturn(entryExecutionResponse());

        service.evaluatePendingOrder(20L);

        assertThat(parent.getStatus()).isEqualTo(TriggerOrderStatus.TRIGGERED);
        assertThat(child.getStatus()).isEqualTo(TriggerOrderStatus.PENDING);
        assertThat(child.getTargetPositionId()).isEqualTo(1L);
    }

    @Test
    void bindsIfoOcoChildrenWhenParentEntryTriggers() {
        TriggerOrder parent = new TriggerOrder();
        ReflectionTestUtils.setField(parent, "id", 30L);
        parent.setPurpose(TriggerOrderPurpose.ENTRY);
        parent.setCurrencyPair("USD/JPY");
        parent.setSide(OrderSide.BUY);
        parent.setOrderType(OrderType.LIMIT);
        parent.setQuantity(new BigDecimal("1000"));
        parent.setTriggerPrice(new BigDecimal("154.500"));
        parent.setStatus(TriggerOrderStatus.PENDING);

        TriggerOrder takeProfit = new TriggerOrder();
        ReflectionTestUtils.setField(takeProfit, "id", 31L);
        takeProfit.setPurpose(TriggerOrderPurpose.EXIT);
        takeProfit.setExitType(ExitOrderType.TP);
        takeProfit.setParentOrderId(30L);
        takeProfit.setCurrencyPair("USD/JPY");
        takeProfit.setSide(OrderSide.SELL);
        takeProfit.setOrderType(OrderType.LIMIT);
        takeProfit.setQuantity(new BigDecimal("1000"));
        takeProfit.setTriggerPrice(new BigDecimal("156.000"));
        takeProfit.setStatus(TriggerOrderStatus.PENDING);
        takeProfit.setOcoGroupId("ifo-oco-1");

        TriggerOrder stopLoss = new TriggerOrder();
        ReflectionTestUtils.setField(stopLoss, "id", 32L);
        stopLoss.setPurpose(TriggerOrderPurpose.EXIT);
        stopLoss.setExitType(ExitOrderType.SL);
        stopLoss.setParentOrderId(30L);
        stopLoss.setCurrencyPair("USD/JPY");
        stopLoss.setSide(OrderSide.SELL);
        stopLoss.setOrderType(OrderType.STOP);
        stopLoss.setQuantity(new BigDecimal("1000"));
        stopLoss.setTriggerPrice(new BigDecimal("154.000"));
        stopLoss.setStatus(TriggerOrderStatus.PENDING);
        stopLoss.setOcoGroupId("ifo-oco-1");

        when(triggerOrderRepository.findById(30L)).thenReturn(Optional.of(parent));
        when(triggerOrderRepository.findByParentOrderIdAndStatusInOrderByCreatedAtAsc(
                30L,
                List.of(TriggerOrderStatus.PENDING, TriggerOrderStatus.WAITING)
        )).thenReturn(List.of(takeProfit, stopLoss));
        mockOpenPosition(PositionSide.LONG);
        mockCurrencyPair();
        mockMarketRate("154.497", "154.500");
        when(tradeExecutionService.executeTriggeredOrder(
                "USD/JPY",
                OrderSide.BUY,
                new BigDecimal("1000"),
                OrderType.LIMIT
        )).thenReturn(entryExecutionResponse());

        service.evaluatePendingOrder(30L);

        assertThat(parent.getStatus()).isEqualTo(TriggerOrderStatus.TRIGGERED);
        assertThat(takeProfit.getStatus()).isEqualTo(TriggerOrderStatus.PENDING);
        assertThat(stopLoss.getStatus()).isEqualTo(TriggerOrderStatus.PENDING);
        assertThat(takeProfit.getTargetPositionId()).isEqualTo(1L);
        assertThat(stopLoss.getTargetPositionId()).isEqualTo(1L);
        assertThat(takeProfit.getOcoGroupId()).isEqualTo(stopLoss.getOcoGroupId());
    }

    private void mockDefaultAccount() {
        Account account = org.mockito.Mockito.mock(Account.class);
        when(account.getId()).thenReturn(100L);
        when(accountRepository.findByAccountNumber("DEMO-ACCOUNT-001")).thenReturn(Optional.of(account));
    }

    private TriggerOrder standaloneExitOrder() {
        TriggerOrder order = new TriggerOrder();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setAccountId(100L);
        order.setPurpose(TriggerOrderPurpose.EXIT);
        order.setExitType(ExitOrderType.TP);
        order.setTargetPositionId(1L);
        order.setCurrencyPair("USD/JPY");
        order.setSide(OrderSide.SELL);
        order.setOrderType(OrderType.LIMIT);
        order.setQuantity(new BigDecimal("1000"));
        order.setTriggerPrice(new BigDecimal("156.000"));
        order.setStatus(TriggerOrderStatus.PENDING);
        return order;
    }

    private PositionExitOrderAmendRequest exitAmendRequest(String triggerPrice) {
        PositionExitOrderAmendRequest request = new PositionExitOrderAmendRequest();
        request.setTriggerPrice(new BigDecimal(triggerPrice));
        return request;
    }

    private void assertResponseStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(status);
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
                        new BigDecimal("1000"),
                        OrderSource.TRIGGER.name()
                )
        );
        return new PositionCloseResponse(
                1L,
                "USD/JPY",
                "LONG",
                new BigDecimal("1000"),
                new BigDecimal("156.000"),
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                "JPY",
                now,
                execution
        );
    }

    private OrderResultResponse entryExecutionResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 12, 0);
        return new OrderResultResponse(
                new OrderSummaryResponse(
                        77L,
                        "USD/JPY",
                        "BUY",
                        "LIMIT",
                        new BigDecimal("1000"),
                        "EXECUTED",
                        "TRIGGER",
                        now
                ),
                new TradeSummaryResponse(
                        78L,
                        77L,
                        "USD/JPY",
                        "BUY",
                        new BigDecimal("1000"),
                        new BigDecimal("154.500"),
                        now,
                        "OPEN",
                        1L,
                        null,
                        OrderSource.TRIGGER.name()
                )
        );
    }

    private static class SaveTriggerOrderAnswer implements org.mockito.stubbing.Answer<TriggerOrder> {
        private long nextId = 100L;

        @Override
        public TriggerOrder answer(org.mockito.invocation.InvocationOnMock invocation) {
            TriggerOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", nextId++);
            }
            return order;
        }
    }
}
