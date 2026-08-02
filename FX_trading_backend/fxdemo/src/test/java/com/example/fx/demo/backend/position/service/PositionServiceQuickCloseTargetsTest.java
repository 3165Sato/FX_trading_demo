package com.example.fx.demo.backend.position.service;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import com.example.fx.demo.backend.common.enums.QuickCloseScope;
import com.example.fx.demo.backend.margin.config.MarginProperties;
import com.example.fx.demo.backend.margin.policy.MarginAggregationPolicy;
import com.example.fx.demo.backend.margin.policy.MarginRateEvaluationPolicy;
import com.example.fx.demo.backend.margin.repository.MarginRuleRepository;
import com.example.fx.demo.backend.margin.service.CurrencyConverter;
import com.example.fx.demo.backend.market.pair.CurrencyPair;
import com.example.fx.demo.backend.market.pair.CurrencyPairRepository;
import com.example.fx.demo.backend.market.rate.MarketRateRepository;
import com.example.fx.demo.backend.order.repository.FxOrderRepository;
import com.example.fx.demo.backend.order.repository.TriggerOrderRepository;
import com.example.fx.demo.backend.position.domain.Position;
import com.example.fx.demo.backend.position.repository.PositionRepository;
import com.example.fx.demo.backend.position.repository.SwapRealizationRepository;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.repository.TradeRepository;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionServiceQuickCloseTargetsTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AccountTradeLockService accountTradeLockService;
    @Mock private CurrencyPairRepository currencyPairRepository;
    @Mock private CurrencyConverter currencyConverter;
    @Mock private FxOrderRepository fxOrderRepository;
    @Mock private MarginProperties marginProperties;
    @Mock private MarginRuleRepository marginRuleRepository;
    @Mock private MarketRateRepository marketRateRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private SwapRealizationRepository swapRealizationRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private TriggerOrderRepository triggerOrderRepository;

    private PositionService service;

    @BeforeEach
    void setUp() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", 7L);
        when(accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(account));
        service = new PositionService(
                accountRepository,
                accountTradeLockService,
                currencyPairRepository,
                currencyConverter,
                fxOrderRepository,
                List.<MarginAggregationPolicy>of(),
                marginProperties,
                List.<MarginRateEvaluationPolicy>of(),
                marginRuleRepository,
                marketRateRepository,
                positionRepository,
                swapRealizationRepository,
                tradeRepository,
                triggerOrderRepository
        );
    }

    @Test
    void pairScopeReturnsOnlyOpenTargetsForTheRequestedEnabledPair() {
        CurrencyPair pair = new CurrencyPair();
        pair.setSymbol("EUR/USD");
        pair.setEnabled(true);
        when(currencyPairRepository.findBySymbol("EUR/USD")).thenReturn(Optional.of(pair));
        when(positionRepository.findByAccountIdAndCurrencyPairAndStatusOrderByOpenedAtAsc(
                7L, "EUR/USD", PositionStatus.OPEN
        )).thenReturn(List.of(position(11L, "EUR/USD"), position(12L, "EUR/USD")));

        var targets = service.findOpenQuickCloseTargetsForLockedAccount(QuickCloseScope.PAIR, "EUR/USD");

        assertThat(targets).extracting("positionId", "currencyPair")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(11L, "EUR/USD"),
                        org.assertj.core.groups.Tuple.tuple(12L, "EUR/USD")
                );
        verify(positionRepository).findByAccountIdAndCurrencyPairAndStatusOrderByOpenedAtAsc(
                7L, "EUR/USD", PositionStatus.OPEN
        );
        verify(positionRepository, never()).findByAccountIdAndStatusOrderByOpenedAtAsc(7L, PositionStatus.OPEN);
    }

    @Test
    void accountScopeReturnsOpenTargetsAcrossPairs() {
        when(positionRepository.findByAccountIdAndStatusOrderByOpenedAtAsc(7L, PositionStatus.OPEN))
                .thenReturn(List.of(position(21L, "USD/JPY"), position(22L, "EUR/USD")));

        var targets = service.findOpenQuickCloseTargetsForLockedAccount(QuickCloseScope.ACCOUNT, null);

        assertThat(targets).extracting("positionId", "currencyPair")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(21L, "USD/JPY"),
                        org.assertj.core.groups.Tuple.tuple(22L, "EUR/USD")
                );
        verify(positionRepository).findByAccountIdAndStatusOrderByOpenedAtAsc(7L, PositionStatus.OPEN);
    }

    @Test
    void pairScopeRejectsUnknownPairBeforeLoadingPositions() {
        when(currencyPairRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOpenQuickCloseTargetsForLockedAccount(
                QuickCloseScope.PAIR, "UNKNOWN"
        )).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(positionRepository, never()).findByAccountIdAndCurrencyPairAndStatusOrderByOpenedAtAsc(
                7L, "UNKNOWN", PositionStatus.OPEN
        );
    }

    @Test
    void pairScopeRejectsDisabledPairBeforeLoadingPositions() {
        CurrencyPair pair = new CurrencyPair();
        pair.setSymbol("EUR/USD");
        pair.setEnabled(false);
        when(currencyPairRepository.findBySymbol("EUR/USD")).thenReturn(Optional.of(pair));

        assertThatThrownBy(() -> service.findOpenQuickCloseTargetsForLockedAccount(
                QuickCloseScope.PAIR, "EUR/USD"
        )).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(positionRepository, never()).findByAccountIdAndCurrencyPairAndStatusOrderByOpenedAtAsc(
                7L, "EUR/USD", PositionStatus.OPEN
        );
    }

    private Position position(long id, String currencyPair) {
        Position position = new Position();
        ReflectionTestUtils.setField(position, "id", id);
        position.setCurrencyPair(currencyPair);
        position.setStatus(PositionStatus.OPEN);
        return position;
    }
}
