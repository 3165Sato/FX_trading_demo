package com.example.fx.demo.backend.cash.service;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.account.service.AccountSummaryService;
import com.example.fx.demo.backend.cash.config.CashProperties;
import com.example.fx.demo.backend.cash.domain.CashTransaction;
import com.example.fx.demo.backend.cash.dto.CashOperationResponse;
import com.example.fx.demo.backend.cash.dto.CashTransactionResponse;
import com.example.fx.demo.backend.cash.repository.CashTransactionRepository;
import com.example.fx.demo.backend.common.enums.CashTransactionStatus;
import com.example.fx.demo.backend.common.enums.CashTransactionType;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CashService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AccountRepository accountRepository;
    private final AccountSummaryService accountSummaryService;
    private final AccountTradeLockService accountTradeLockService;
    private final CashProperties cashProperties;
    private final CashTransactionRepository cashTransactionRepository;

    public CashService(
            AccountRepository accountRepository,
            AccountSummaryService accountSummaryService,
            AccountTradeLockService accountTradeLockService,
            CashProperties cashProperties,
            CashTransactionRepository cashTransactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountSummaryService = accountSummaryService;
        this.accountTradeLockService = accountTradeLockService;
        this.cashProperties = cashProperties;
        this.cashTransactionRepository = cashTransactionRepository;
    }

    @Transactional
    public CashOperationResponse deposit(BigDecimal requestedAmount) {
        BigDecimal amount = normalizePositiveAmount(requestedAmount);
        BigDecimal maxDeposit = cashProperties.getMaxDepositPerRequest().setScale(0, RoundingMode.HALF_UP);
        if (amount.compareTo(maxDeposit) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "入金額が1回あたりの上限を超えています。");
        }
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> depositLocked(amount)
        );
    }

    @Transactional
    public CashOperationResponse withdraw(BigDecimal requestedAmount) {
        BigDecimal amount = normalizePositiveAmount(requestedAmount);
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> withdrawLocked(amount)
        );
    }

    @Transactional(readOnly = true)
    public BigDecimal getWithdrawable() {
        return accountSummaryService.getDefaultAccountSummary().withdrawable();
    }

    @Transactional(readOnly = true)
    public List<CashTransactionResponse> getTransactions(Integer limit) {
        Account account = defaultAccount();
        int normalizedLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        return cashTransactionRepository.findByAccountIdOrderByCompletedAtDesc(
                        account.getId(),
                        PageRequest.of(0, normalizedLimit)
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    private CashOperationResponse depositLocked(BigDecimal amount) {
        Account account = defaultAccount();
        BigDecimal balanceAfter = currentBalance(account).add(amount).setScale(0, RoundingMode.HALF_UP);
        account.setBalance(balanceAfter);
        accountRepository.save(account);
        CashTransaction transaction = recordTransaction(account, CashTransactionType.DEPOSIT, amount);
        return new CashOperationResponse(toResponse(transaction), balanceAfter);
    }

    private CashOperationResponse withdrawLocked(BigDecimal amount) {
        AccountSummaryResponse summary = accountSummaryService.getDefaultAccountSummary();
        BigDecimal withdrawable = summary.withdrawable();
        if (withdrawable == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "出金可能額を算出できないため出金できません。");
        }
        if (amount.compareTo(withdrawable) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "出金額が出金可能額を超えています。");
        }

        Account account = defaultAccount();
        BigDecimal balanceAfter = currentBalance(account).subtract(amount).setScale(0, RoundingMode.HALF_UP);
        account.setBalance(balanceAfter);
        accountRepository.save(account);
        CashTransaction transaction = recordTransaction(account, CashTransactionType.WITHDRAWAL, amount);
        return new CashOperationResponse(toResponse(transaction), balanceAfter);
    }

    private CashTransaction recordTransaction(Account account, CashTransactionType type, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();
        CashTransaction transaction = new CashTransaction();
        transaction.setAccountId(account.getId());
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setStatus(CashTransactionStatus.COMPLETED);
        transaction.setRequestedAt(now);
        transaction.setCompletedAt(now);
        return cashTransactionRepository.save(transaction);
    }

    private BigDecimal normalizePositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金額を指定してください。");
        }
        BigDecimal normalized = amount.setScale(0, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "金額は0より大きい値を指定してください。");
        }
        return normalized;
    }

    private BigDecimal currentBalance(Account account) {
        return (account.getBalance() == null ? BigDecimal.ZERO : account.getBalance())
                .setScale(0, RoundingMode.HALF_UP);
    }

    private Account defaultAccount() {
        return accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
    }

    private CashTransactionResponse toResponse(CashTransaction transaction) {
        return new CashTransactionResponse(
                transaction.getId(),
                transaction.getTransactionType().name(),
                transaction.getAmount(),
                transaction.getStatus().name(),
                transaction.getRequestedAt(),
                transaction.getCompletedAt()
        );
    }
}
