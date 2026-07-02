package com.example.fx.demo.backend.account.service;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.domain.EquitySnapshot;
import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.account.dto.EquitySnapshotResponse;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.account.repository.EquitySnapshotRepository;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class EquitySnapshotService {

    private static final int DEFAULT_LIMIT = 300;
    private static final int MAX_LIMIT = 1000;

    private final AccountRepository accountRepository;
    private final AccountSummaryService accountSummaryService;
    private final EquitySnapshotRepository equitySnapshotRepository;

    public EquitySnapshotService(
            AccountRepository accountRepository,
            AccountSummaryService accountSummaryService,
            EquitySnapshotRepository equitySnapshotRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountSummaryService = accountSummaryService;
        this.equitySnapshotRepository = equitySnapshotRepository;
    }

    @Transactional
    public Optional<EquitySnapshotResponse> recordDefaultAccountSnapshot() {
        AccountSummaryResponse summary = accountSummaryService.getDefaultAccountSummary();
        if (summary.equity() == null) {
            return Optional.empty();
        }

        Account account = findDefaultAccount();
        EquitySnapshot snapshot = new EquitySnapshot();
        snapshot.setAccount(account);
        snapshot.setBalance(summary.balance());
        snapshot.setEquity(summary.equity());
        snapshot.setUsedMargin(summary.usedMargin());
        snapshot.setMarginRatio(summary.marginRatio());
        snapshot.setRecordedAt(Instant.now());

        return Optional.of(toResponse(equitySnapshotRepository.save(snapshot)));
    }

    @Transactional(readOnly = true)
    public List<EquitySnapshotResponse> getDefaultAccountHistory(Integer limit, Instant from) {
        int normalizedLimit = normalizeLimit(limit);
        String accountNumber = DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER;
        PageRequest pageRequest = PageRequest.of(0, normalizedLimit);
        List<EquitySnapshot> snapshots = from == null
                ? equitySnapshotRepository.findByAccount_AccountNumberOrderByRecordedAtDesc(accountNumber, pageRequest)
                : equitySnapshotRepository.findByAccount_AccountNumberAndRecordedAtAfterOrderByRecordedAtDesc(
                        accountNumber,
                        from,
                        pageRequest
                );

        return snapshots.stream()
                .sorted(Comparator.comparing(EquitySnapshot::getRecordedAt))
                .map(this::toResponse)
                .toList();
    }

    private Account findDefaultAccount() {
        return accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private EquitySnapshotResponse toResponse(EquitySnapshot snapshot) {
        return new EquitySnapshotResponse(
                snapshot.getRecordedAt(),
                snapshot.getBalance(),
                snapshot.getEquity(),
                snapshot.getUsedMargin(),
                snapshot.getMarginRatio()
        );
    }
}
