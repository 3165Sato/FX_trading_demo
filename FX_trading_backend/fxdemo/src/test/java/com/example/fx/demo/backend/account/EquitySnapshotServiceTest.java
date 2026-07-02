package com.example.fx.demo.backend.account;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.domain.EquitySnapshot;
import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.account.dto.EquitySnapshotResponse;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.account.repository.EquitySnapshotRepository;
import com.example.fx.demo.backend.account.service.AccountSummaryService;
import com.example.fx.demo.backend.account.service.EquitySnapshotService;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquitySnapshotServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountSummaryService accountSummaryService;

    @Mock
    private EquitySnapshotRepository equitySnapshotRepository;

    private EquitySnapshotService service;

    @BeforeEach
    void setUp() {
        service = new EquitySnapshotService(
                accountRepository,
                accountSummaryService,
                equitySnapshotRepository
        );
    }

    @Test
    void recordsSnapshotFromAccountSummary() {
        Account account = account();
        when(accountSummaryService.getDefaultAccountSummary()).thenReturn(summary("1000000", "1002500", "250000", "401.00"));
        when(accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(account));
        when(equitySnapshotRepository.save(isA(EquitySnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<EquitySnapshotResponse> response = service.recordDefaultAccountSnapshot();

        ArgumentCaptor<EquitySnapshot> captor = ArgumentCaptor.forClass(EquitySnapshot.class);
        verify(equitySnapshotRepository).save(captor.capture());
        EquitySnapshot snapshot = captor.getValue();
        assertThat(response).isPresent();
        assertThat(snapshot.getAccount()).isSameAs(account);
        assertThat(snapshot.getBalance()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(snapshot.getEquity()).isEqualByComparingTo(new BigDecimal("1002500"));
        assertThat(snapshot.getUsedMargin()).isEqualByComparingTo(new BigDecimal("250000"));
        assertThat(snapshot.getMarginRatio()).isEqualByComparingTo(new BigDecimal("401.00"));
        assertThat(snapshot.getRecordedAt()).isNotNull();
    }

    @Test
    void skipsSnapshotWhenEquityIsUnavailable() {
        when(accountSummaryService.getDefaultAccountSummary()).thenReturn(summary("1000000", null, "250000", null));

        Optional<EquitySnapshotResponse> response = service.recordDefaultAccountSnapshot();

        assertThat(response).isEmpty();
        verify(accountRepository, never()).findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER);
        verify(equitySnapshotRepository, never()).save(isA(EquitySnapshot.class));
    }

    @Test
    void returnsHistoryInRecordedAtAscendingOrderWithLimit() {
        EquitySnapshot newer = snapshot("1000000", "1002000", "2026-07-02T10:00:10Z");
        EquitySnapshot older = snapshot("1000000", "1001000", "2026-07-02T10:00:05Z");
        when(equitySnapshotRepository.findByAccount_AccountNumberOrderByRecordedAtDesc(
                eq(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER),
                isA(Pageable.class)
        )).thenReturn(List.of(newer, older));

        List<EquitySnapshotResponse> responses = service.getDefaultAccountHistory(300, null);

        assertThat(responses).extracting(EquitySnapshotResponse::recordedAt)
                .containsExactly(older.getRecordedAt(), newer.getRecordedAt());
        assertThat(responses).extracting(EquitySnapshotResponse::equity)
                .containsExactly(new BigDecimal("1001000"), new BigDecimal("1002000"));
    }

    @Test
    void supportsFromFilterAndNormalizesLimit() {
        Instant from = Instant.parse("2026-07-02T10:00:00Z");
        when(equitySnapshotRepository.findByAccount_AccountNumberAndRecordedAtAfterOrderByRecordedAtDesc(
                eq(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER),
                eq(from),
                isA(Pageable.class)
        )).thenReturn(List.of());

        List<EquitySnapshotResponse> responses = service.getDefaultAccountHistory(2000, from);

        assertThat(responses).isEmpty();
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(equitySnapshotRepository).findByAccount_AccountNumberAndRecordedAtAfterOrderByRecordedAtDesc(
                eq(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER),
                eq(from),
                captor.capture()
        );
        assertThat(captor.getValue().getPageSize()).isEqualTo(1000);
    }

    private Account account() {
        Account account = new Account();
        account.setAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER);
        return account;
    }

    private AccountSummaryResponse summary(String balance, String equity, String usedMargin, String marginRatio) {
        return new AccountSummaryResponse(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                "JPY",
                decimal(balance),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                decimal(equity),
                decimal(usedMargin),
                BigDecimal.ZERO,
                decimal(marginRatio),
                new BigDecimal("50"),
                "SAFE"
        );
    }

    private EquitySnapshot snapshot(String balance, String equity, String recordedAt) {
        EquitySnapshot snapshot = new EquitySnapshot();
        snapshot.setAccount(account());
        snapshot.setBalance(new BigDecimal(balance));
        snapshot.setEquity(new BigDecimal(equity));
        snapshot.setUsedMargin(BigDecimal.ZERO);
        snapshot.setRecordedAt(Instant.parse(recordedAt));
        return snapshot;
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
