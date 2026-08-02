package com.example.fx.demo.backend.position.service;

import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.common.enums.QuickCloseScope;
import com.example.fx.demo.backend.position.dto.PositionCloseResponse;
import com.example.fx.demo.backend.position.dto.QuickCloseRequest;
import com.example.fx.demo.backend.position.dto.QuickCloseResponse;
import com.example.fx.demo.backend.position.model.QuickCloseTarget;
import com.example.fx.demo.backend.trade.dto.OrderResultResponse;
import com.example.fx.demo.backend.trade.dto.OrderSummaryResponse;
import com.example.fx.demo.backend.trade.dto.TradeSummaryResponse;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickCloseServiceTest {

    @Mock
    private PositionService positionService;

    private QuickCloseService service;

    @BeforeEach
    void setUp() {
        service = new QuickCloseService(new AccountTradeLockService(), positionService);
    }

    @Test
    void closesAllAccountTargetsWithQuickCloseSource() {
        QuickCloseRequest request = new QuickCloseRequest(QuickCloseScope.ACCOUNT, null);
        when(positionService.findOpenQuickCloseTargetsForLockedAccount(QuickCloseScope.ACCOUNT, null))
                .thenReturn(List.of(target(1L, "USD/JPY"), target(2L, "EUR/JPY")));
        when(positionService.closePositionForLockedAccount(1L, OrderSource.QUICK_CLOSE))
                .thenReturn(closeResponse(1L, "USD/JPY"));
        when(positionService.closePositionForLockedAccount(2L, OrderSource.QUICK_CLOSE))
                .thenReturn(closeResponse(2L, "EUR/JPY"));

        QuickCloseResponse result = service.quickClose(request);

        assertThat(result.scope()).isEqualTo(QuickCloseScope.ACCOUNT);
        assertThat(result.currencyPair()).isNull();
        assertThat(result.targetCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isZero();
        assertThat(result.successes()).extracting(PositionCloseResponse::positionId).containsExactly(1L, 2L);
        verify(positionService).closePositionForLockedAccount(1L, OrderSource.QUICK_CLOSE);
        verify(positionService).closePositionForLockedAccount(2L, OrderSource.QUICK_CLOSE);
    }

    @Test
    void delegatesPairScopeWithoutUsingTradingSelection() {
        QuickCloseRequest request = new QuickCloseRequest(QuickCloseScope.PAIR, "EUR/USD");
        when(positionService.findOpenQuickCloseTargetsForLockedAccount(QuickCloseScope.PAIR, "EUR/USD"))
                .thenReturn(List.of(target(3L, "EUR/USD")));
        when(positionService.closePositionForLockedAccount(3L, OrderSource.QUICK_CLOSE))
                .thenReturn(closeResponse(3L, "EUR/USD"));

        QuickCloseResponse result = service.quickClose(request);

        assertThat(result.currencyPair()).isEqualTo("EUR/USD");
        assertThat(result.successCount()).isEqualTo(1);
        verify(positionService).findOpenQuickCloseTargetsForLockedAccount(QuickCloseScope.PAIR, "EUR/USD");
    }

    @Test
    void continuesAfterKnownFailureAndReturnsFailureDetails() {
        QuickCloseRequest request = new QuickCloseRequest(QuickCloseScope.ACCOUNT, null);
        when(positionService.findOpenQuickCloseTargetsForLockedAccount(QuickCloseScope.ACCOUNT, null))
                .thenReturn(List.of(target(1L, "USD/JPY"), target(2L, "EUR/JPY")));
        when(positionService.closePositionForLockedAccount(1L, OrderSource.QUICK_CLOSE))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "最新レートがありません。"));
        when(positionService.closePositionForLockedAccount(2L, OrderSource.QUICK_CLOSE))
                .thenReturn(closeResponse(2L, "EUR/JPY"));

        QuickCloseResponse result = service.quickClose(request);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.positionId()).isEqualTo(1L);
            assertThat(failure.currencyPair()).isEqualTo("USD/JPY");
            assertThat(failure.reason()).isEqualTo("最新レートがありません。");
        });
        verify(positionService).closePositionForLockedAccount(2L, OrderSource.QUICK_CLOSE);
    }

    @Test
    void generalizesUnexpectedFailureAndContinues() {
        QuickCloseRequest request = new QuickCloseRequest(QuickCloseScope.ACCOUNT, null);
        when(positionService.findOpenQuickCloseTargetsForLockedAccount(QuickCloseScope.ACCOUNT, null))
                .thenReturn(List.of(target(1L, "USD/JPY"), target(2L, "EUR/JPY")));
        when(positionService.closePositionForLockedAccount(1L, OrderSource.QUICK_CLOSE))
                .thenThrow(new IllegalStateException("database details"));
        when(positionService.closePositionForLockedAccount(2L, OrderSource.QUICK_CLOSE))
                .thenReturn(closeResponse(2L, "EUR/JPY"));

        QuickCloseResponse result = service.quickClose(request);

        assertThat(result.failures()).singleElement().satisfies(failure ->
                assertThat(failure.reason()).isEqualTo("決済処理に失敗しました。"));
        assertThat(result.successCount()).isEqualTo(1);
    }

    @Test
    void returnsConflictWhenNoOpenTargetsExist() {
        when(positionService.findOpenQuickCloseTargetsForLockedAccount(QuickCloseScope.ACCOUNT, null))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.quickClose(new QuickCloseRequest(QuickCloseScope.ACCOUNT, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo("対象となるOPEN建玉がありません。");
                });
        verify(positionService, never()).closePositionForLockedAccount(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(OrderSource.class)
        );
    }

    @Test
    void rejectsInvalidScopeAndPairCombinations() {
        assertBadRequest(null);
        assertBadRequest(new QuickCloseRequest(null, null));
        assertBadRequest(new QuickCloseRequest(QuickCloseScope.PAIR, " "));
        assertBadRequest(new QuickCloseRequest(QuickCloseScope.ACCOUNT, "USD/JPY"));
        verify(positionService, never()).findOpenQuickCloseTargetsForLockedAccount(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void propagatesPairValidationFailureBeforeClosingTargets() {
        QuickCloseRequest request = new QuickCloseRequest(QuickCloseScope.PAIR, "UNKNOWN");
        when(positionService.findOpenQuickCloseTargetsForLockedAccount(QuickCloseScope.PAIR, "UNKNOWN"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Currency pair not found: UNKNOWN"));

        assertThatThrownBy(() -> service.quickClose(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private void assertBadRequest(QuickCloseRequest request) {
        assertThatThrownBy(() -> service.quickClose(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private QuickCloseTarget target(Long id, String currencyPair) {
        return new QuickCloseTarget(id, currencyPair);
    }

    private PositionCloseResponse closeResponse(Long positionId, String currencyPair) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);
        OrderResultResponse execution = new OrderResultResponse(
                new OrderSummaryResponse(
                        positionId,
                        currencyPair,
                        "SELL",
                        "MARKET",
                        new BigDecimal("1000"),
                        "EXECUTED",
                        OrderSource.QUICK_CLOSE.name(),
                        now
                ),
                new TradeSummaryResponse(
                        positionId,
                        positionId,
                        currencyPair,
                        "SELL",
                        new BigDecimal("1000"),
                        new BigDecimal("155.000"),
                        now,
                        "CLOSE",
                        positionId,
                        BigDecimal.ZERO,
                        OrderSource.QUICK_CLOSE.name()
                )
        );
        return new PositionCloseResponse(
                positionId,
                currencyPair,
                "LONG",
                new BigDecimal("1000"),
                new BigDecimal("155.000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "JPY",
                now,
                execution
        );
    }
}
