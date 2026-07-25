package com.example.fx.demo.backend.cash;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.account.service.AccountSummaryService;
import com.example.fx.demo.backend.cash.config.CashProperties;
import com.example.fx.demo.backend.cash.domain.CashTransaction;
import com.example.fx.demo.backend.cash.dto.CashOperationResponse;
import com.example.fx.demo.backend.cash.repository.CashTransactionRepository;
import com.example.fx.demo.backend.cash.service.CashService;
import com.example.fx.demo.backend.common.enums.CashTransactionStatus;
import com.example.fx.demo.backend.common.enums.CashTransactionType;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashServiceTest {

    private Account account;
    private AccountRepository accountRepository;
    private AccountSummaryService accountSummaryService;
    private CashTransactionRepository cashTransactionRepository;
    private CashProperties cashProperties;
    private CashService service;

    @BeforeEach
    void setUp() {
        account = new Account();
        ReflectionTestUtils.setField(account, "id", 1L);
        account.setAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER);
        account.setBalance(new BigDecimal("1000000"));

        accountRepository = mock(AccountRepository.class);
        accountSummaryService = mock(AccountSummaryService.class);
        cashTransactionRepository = mock(CashTransactionRepository.class);
        cashProperties = new CashProperties();
        service = new CashService(
                accountRepository,
                accountSummaryService,
                new AccountTradeLockService(),
                cashProperties,
                cashTransactionRepository
        );

        when(accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(account));
        when(cashTransactionRepository.save(any(CashTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void depositsAndRecordsCompletedTransaction() {
        CashOperationResponse response = service.deposit(new BigDecimal("500000"));

        assertThat(response.balanceAfter()).isEqualByComparingTo(new BigDecimal("1500000"));
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("1500000"));
        ArgumentCaptor<CashTransaction> captor = ArgumentCaptor.forClass(CashTransaction.class);
        verify(cashTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionType()).isEqualTo(CashTransactionType.DEPOSIT);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(captor.getValue().getStatus()).isEqualTo(CashTransactionStatus.COMPLETED);
    }

    @Test
    void rejectsDepositAboveConfiguredLimit() {
        cashProperties.setMaxDepositPerRequest(new BigDecimal("100000000"));

        assertThatThrownBy(() -> service.deposit(new BigDecimal("100000001")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void withdrawsExactlyWithdrawableAmount() {
        when(accountSummaryService.getDefaultAccountSummary()).thenReturn(summary("918000"));

        CashOperationResponse response = service.withdraw(new BigDecimal("918000"));

        assertThat(response.balanceAfter()).isEqualByComparingTo(new BigDecimal("82000"));
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("82000"));
        ArgumentCaptor<CashTransaction> captor = ArgumentCaptor.forClass(CashTransaction.class);
        verify(cashTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionType()).isEqualTo(CashTransactionType.WITHDRAWAL);
    }

    @Test
    void rejectsWithdrawalAboveWithdrawableAmount() {
        when(accountSummaryService.getDefaultAccountSummary()).thenReturn(summary("918000"));

        assertThatThrownBy(() -> service.withdraw(new BigDecimal("918001")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void rejectsWithdrawalWhenWithdrawableCannotBeCalculated() {
        when(accountSummaryService.getDefaultAccountSummary()).thenReturn(summary(null));

        assertThatThrownBy(() -> service.withdraw(new BigDecimal("1000")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertThatThrownBy(() -> service.deposit(BigDecimal.ZERO))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
        assertThatThrownBy(() -> service.withdraw(new BigDecimal("-1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void returnsNewestTransactionsWithNormalizedLimit() {
        CashTransaction transaction = transaction();
        when(cashTransactionRepository.findByAccountIdOrderByCompletedAtDesc(any(Long.class), any(Pageable.class)))
                .thenReturn(List.of(transaction));

        var responses = service.getTransactions(1000);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().type()).isEqualTo("DEPOSIT");
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(cashTransactionRepository).findByAccountIdOrderByCompletedAtDesc(any(Long.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(200);
    }

    private AccountSummaryResponse summary(String withdrawable) {
        return new AccountSummaryResponse(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                "JPY",
                account.getBalance(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                account.getBalance(),
                BigDecimal.ZERO,
                account.getBalance(),
                decimal(withdrawable),
                null,
                new BigDecimal("50"),
                "SAFE"
        );
    }

    private CashTransaction transaction() {
        CashTransaction transaction = new CashTransaction();
        transaction.setAccountId(1L);
        transaction.setTransactionType(CashTransactionType.DEPOSIT);
        transaction.setAmount(new BigDecimal("1000000"));
        transaction.setStatus(CashTransactionStatus.COMPLETED);
        transaction.setRequestedAt(LocalDateTime.of(2026, 7, 14, 12, 0));
        transaction.setCompletedAt(LocalDateTime.of(2026, 7, 14, 12, 0));
        return transaction;
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
