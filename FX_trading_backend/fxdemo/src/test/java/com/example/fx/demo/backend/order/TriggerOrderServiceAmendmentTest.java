package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.TriggerOrderPurpose;
import com.example.fx.demo.backend.common.enums.TriggerOrderStatus;
import com.example.fx.demo.backend.market.pair.CurrencyPair;
import com.example.fx.demo.backend.market.pair.CurrencyPairRepository;
import com.example.fx.demo.backend.market.rate.MarketRate;
import com.example.fx.demo.backend.market.rate.MarketRateRepository;
import com.example.fx.demo.backend.order.domain.TriggerOrder;
import com.example.fx.demo.backend.order.dto.PendingOrderAmendRequest;
import com.example.fx.demo.backend.order.repository.TriggerOrderRepository;
import com.example.fx.demo.backend.order.service.TriggerOrderService;
import com.example.fx.demo.backend.position.repository.PositionRepository;
import com.example.fx.demo.backend.position.service.PositionService;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import com.example.fx.demo.backend.trade.service.TradeExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TriggerOrderServiceAmendmentTest {

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
    void amendsPendingEntryPriceAndQuantityWithPairScales() {
        TriggerOrder order = pendingEntry();
        mockDependencies(order);
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setQuantity(new BigDecimal("1234.6"));
        request.setTriggerPrice(new BigDecimal("154.5555"));

        var response = service.amendPendingOrder(10L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.quantity()).isEqualByComparingTo("1235");
        assertThat(response.triggerPrice()).isEqualByComparingTo("154.556");
        assertThat(order.getQuantity()).isEqualByComparingTo("1235");
        assertThat(order.getTriggerPrice()).isEqualByComparingTo("154.556");
    }

    @Test
    void amendsOnlySpecifiedPrice() {
        TriggerOrder order = pendingEntry();
        mockDependencies(order);
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setTriggerPrice(new BigDecimal("154.750"));

        service.amendPendingOrder(10L, request);

        assertThat(order.getQuantity()).isEqualByComparingTo("1000");
        assertThat(order.getTriggerPrice()).isEqualByComparingTo("154.750");
    }

    @Test
    void rejectsRequestWithoutFields() {
        assertStatus(
                () -> service.amendPendingOrder(10L, new PendingOrderAmendRequest()),
                HttpStatus.BAD_REQUEST
        );
        verify(triggerOrderRepository, never()).save(any());
    }

    @Test
    void rejectsExplicitNull() {
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setQuantity(null);

        assertStatus(() -> service.amendPendingOrder(10L, request), HttpStatus.BAD_REQUEST);
        verify(triggerOrderRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidDirectionWithoutSaving() {
        TriggerOrder order = pendingEntry();
        mockDependencies(order);
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setTriggerPrice(new BigDecimal("156.000"));

        assertStatus(() -> service.amendPendingOrder(10L, request), HttpStatus.BAD_REQUEST);
        verify(triggerOrderRepository, never()).save(any());
        assertThat(order.getTriggerPrice()).isEqualByComparingTo("154.500");
    }

    @Test
    void rejectsEveryNonPendingStatus() {
        TriggerOrder order = pendingEntry();
        mockAccountAndOrder(order);
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setQuantity(new BigDecimal("2000"));

        for (TriggerOrderStatus status : List.of(
                TriggerOrderStatus.WAITING,
                TriggerOrderStatus.TRIGGERED,
                TriggerOrderStatus.CANCELED,
                TriggerOrderStatus.CANCELLED,
                TriggerOrderStatus.REJECTED,
                TriggerOrderStatus.EXPIRED
        )) {
            order.setStatus(status);
            assertStatus(() -> service.amendPendingOrder(10L, request), HttpStatus.CONFLICT);
        }
        verify(triggerOrderRepository, never()).save(any());
    }

    @Test
    void rejectsCompositeParentOrder() {
        TriggerOrder order = pendingEntry();
        mockAccountAndOrder(order);
        TriggerOrder child = new TriggerOrder();
        child.setParentOrderId(order.getId());
        when(triggerOrderRepository.findByParentOrderIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(child));
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setQuantity(new BigDecimal("2000"));

        assertStatus(() -> service.amendPendingOrder(10L, request), HttpStatus.CONFLICT);
        verify(triggerOrderRepository, never()).save(any());
    }

    @Test
    void rejectsMissingOrder() {
        Account account = org.mockito.Mockito.mock(Account.class);
        when(account.getId()).thenReturn(100L);
        when(accountRepository.findByAccountNumber("DEMO-ACCOUNT-001")).thenReturn(Optional.of(account));
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setQuantity(new BigDecimal("2000"));

        assertStatus(() -> service.amendPendingOrder(999L, request), HttpStatus.NOT_FOUND);
    }

    @Test
    void hidesOrderOwnedByAnotherAccount() {
        TriggerOrder order = pendingEntry();
        order.setAccountId(200L);
        mockAccountAndOrder(order);
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setQuantity(new BigDecimal("2000"));

        assertStatus(() -> service.amendPendingOrder(10L, request), HttpStatus.NOT_FOUND);
        verify(triggerOrderRepository, never()).save(any());
    }

    @Test
    void rejectsExcessivePrecision() {
        TriggerOrder order = pendingEntry();
        mockDependencies(order);
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setQuantity(new BigDecimal("12345678901234567890"));

        assertStatus(() -> service.amendPendingOrder(10L, request), HttpStatus.BAD_REQUEST);
        verify(triggerOrderRepository, never()).save(any());
    }

    @Test
    void rejectsHugeExponentBeforeScaling() {
        TriggerOrder order = pendingEntry();
        mockDependencies(order);
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setTriggerPrice(new BigDecimal("1E+100000"));

        assertStatus(() -> service.amendPendingOrder(10L, request), HttpStatus.BAD_REQUEST);
        verify(triggerOrderRepository, never()).save(any());
    }

    @Test
    void rejectsHugeNegativeExponentBeforeScaling() {
        TriggerOrder order = pendingEntry();
        mockDependencies(order);
        PendingOrderAmendRequest request = new PendingOrderAmendRequest();
        request.setTriggerPrice(new BigDecimal("1E-100000"));

        assertStatus(() -> service.amendPendingOrder(10L, request), HttpStatus.BAD_REQUEST);
        verify(triggerOrderRepository, never()).save(any());
    }

    private TriggerOrder pendingEntry() {
        TriggerOrder order = new TriggerOrder();
        ReflectionTestUtils.setField(order, "id", 10L);
        order.setAccountId(100L);
        order.setCurrencyPair("USD/JPY");
        order.setSide(OrderSide.BUY);
        order.setOrderType(OrderType.LIMIT);
        order.setQuantity(new BigDecimal("1000"));
        order.setTriggerPrice(new BigDecimal("154.500"));
        order.setStatus(TriggerOrderStatus.PENDING);
        order.setPurpose(TriggerOrderPurpose.ENTRY);
        return order;
    }

    private void mockDependencies(TriggerOrder order) {
        mockAccountAndOrder(order);
        CurrencyPair currencyPair = org.mockito.Mockito.mock(CurrencyPair.class);
        when(currencyPair.getSymbol()).thenReturn("USD/JPY");
        when(currencyPair.getEnabled()).thenReturn(true);
        when(currencyPair.getPriceScale()).thenReturn(3);
        when(currencyPair.getQuantityScale()).thenReturn(0);
        when(currencyPairRepository.findBySymbol("USD/JPY")).thenReturn(Optional.of(currencyPair));
        MarketRate marketRate = org.mockito.Mockito.mock(MarketRate.class);
        when(marketRate.getBid()).thenReturn(new BigDecimal("155.120"));
        when(marketRate.getAsk()).thenReturn(new BigDecimal("155.123"));
        when(marketRateRepository.findByCurrencyPair(currencyPair)).thenReturn(Optional.of(marketRate));
        when(triggerOrderRepository.save(any(TriggerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void mockAccountAndOrder(TriggerOrder order) {
        Account account = org.mockito.Mockito.mock(Account.class);
        when(account.getId()).thenReturn(100L);
        when(accountRepository.findByAccountNumber("DEMO-ACCOUNT-001")).thenReturn(Optional.of(account));
        when(triggerOrderRepository.findById(10L)).thenReturn(Optional.of(order));
    }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(status);
    }
}
