package com.example.fx.demo.backend.position.service;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import com.example.fx.demo.backend.common.enums.SwapRealizationSource;
import com.example.fx.demo.backend.position.domain.Position;
import com.example.fx.demo.backend.position.domain.SwapRealization;
import com.example.fx.demo.backend.position.dto.PositionSwapTransferResponse;
import com.example.fx.demo.backend.position.dto.SwapTransferAllResponse;
import com.example.fx.demo.backend.position.repository.PositionRepository;
import com.example.fx.demo.backend.position.repository.SwapRealizationRepository;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SwapTransferService {

    private static final int SWAP_SCALE = 4;

    private final AccountRepository accountRepository;
    private final AccountTradeLockService accountTradeLockService;
    private final PositionRepository positionRepository;
    private final SwapRealizationRepository swapRealizationRepository;

    public SwapTransferService(
            AccountRepository accountRepository,
            AccountTradeLockService accountTradeLockService,
            PositionRepository positionRepository,
            SwapRealizationRepository swapRealizationRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountTradeLockService = accountTradeLockService;
        this.positionRepository = positionRepository;
        this.swapRealizationRepository = swapRealizationRepository;
    }

    @Transactional
    public PositionSwapTransferResponse transfer(Long positionId) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> transferLocked(positionId)
        );
    }

    @Transactional
    public SwapTransferAllResponse transferAll() {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                this::transferAllLocked
        );
    }

    private PositionSwapTransferResponse transferLocked(Long positionId) {
        Account account = defaultAccount();
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found: " + positionId));
        if (!account.getId().equals(position.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Position not found: " + positionId);
        }
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only OPEN positions can transfer accrued swap.");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal transferredSwap = transferPositionSwap(account, position, now);
        return new PositionSwapTransferResponse(
                position.getId(),
                transferredSwap,
                normalizedBalance(account),
                now
        );
    }

    private SwapTransferAllResponse transferAllLocked() {
        Account account = defaultAccount();
        LocalDateTime now = LocalDateTime.now();
        List<Position> positions = positionRepository.findByAccountIdAndStatusOrderByOpenedAtAsc(
                account.getId(),
                PositionStatus.OPEN
        );

        BigDecimal totalTransferredSwap = BigDecimal.ZERO;
        int transferredPositions = 0;
        for (Position position : positions) {
            BigDecimal transferredSwap = transferPositionSwap(account, position, now);
            if (transferredSwap.signum() != 0) {
                transferredPositions++;
            }
            totalTransferredSwap = totalTransferredSwap.add(transferredSwap);
        }

        return new SwapTransferAllResponse(
                transferredPositions,
                totalTransferredSwap.setScale(SWAP_SCALE, RoundingMode.HALF_UP),
                normalizedBalance(account),
                now
        );
    }

    private BigDecimal transferPositionSwap(Account account, Position position, LocalDateTime now) {
        BigDecimal accruedSwap = position.getAccruedSwap() == null
                ? BigDecimal.ZERO
                : position.getAccruedSwap().setScale(SWAP_SCALE, RoundingMode.HALF_UP);
        if (accruedSwap.signum() == 0) {
            return BigDecimal.ZERO.setScale(SWAP_SCALE, RoundingMode.HALF_UP);
        }

        applyRealizedSwap(account, accruedSwap);
        position.setAccruedSwap(BigDecimal.ZERO.setScale(SWAP_SCALE, RoundingMode.HALF_UP));
        positionRepository.save(position);
        saveRealization(account, position, accruedSwap, SwapRealizationSource.TRANSFER, now);
        return accruedSwap;
    }

    private void applyRealizedSwap(Account account, BigDecimal realizedSwap) {
        BigDecimal currentBalance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        BigDecimal currentRealized = account.getRealizedPnl() == null ? BigDecimal.ZERO : account.getRealizedPnl();
        BigDecimal roundedSwap = realizedSwap.setScale(0, RoundingMode.HALF_UP);
        account.setBalance(currentBalance.add(roundedSwap).setScale(0, RoundingMode.HALF_UP));
        account.setRealizedPnl(currentRealized.add(roundedSwap).setScale(0, RoundingMode.HALF_UP));
        accountRepository.save(account);
    }

    private void saveRealization(
            Account account,
            Position position,
            BigDecimal amount,
            SwapRealizationSource source,
            LocalDateTime realizedAt
    ) {
        SwapRealization realization = new SwapRealization();
        realization.setAccountId(account.getId());
        realization.setPositionId(position.getId());
        realization.setAmount(amount.setScale(SWAP_SCALE, RoundingMode.HALF_UP));
        realization.setSource(source);
        realization.setRealizedAt(realizedAt);
        swapRealizationRepository.save(realization);
    }

    private BigDecimal normalizedBalance(Account account) {
        return (account.getBalance() == null ? BigDecimal.ZERO : account.getBalance())
                .setScale(0, RoundingMode.HALF_UP);
    }

    private Account defaultAccount() {
        return accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
    }
}
