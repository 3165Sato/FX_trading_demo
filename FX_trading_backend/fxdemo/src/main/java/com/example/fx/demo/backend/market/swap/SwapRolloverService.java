package com.example.fx.demo.backend.market.swap;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.common.enums.PositionSide;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import com.example.fx.demo.backend.position.domain.Position;
import com.example.fx.demo.backend.position.repository.PositionRepository;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class SwapRolloverService {

    private static final BigDecimal PER_TEN_THOUSAND = new BigDecimal("10000");
    private static final int SWAP_SCALE = 4;

    private final AccountRepository accountRepository;
    private final AccountTradeLockService accountTradeLockService;
    private final PositionRepository positionRepository;
    private final SwapProperties properties;
    private LocalDate lastAutomaticRolloverDate;

    public SwapRolloverService(
            AccountRepository accountRepository,
            AccountTradeLockService accountTradeLockService,
            PositionRepository positionRepository,
            SwapProperties properties
    ) {
        this.accountRepository = accountRepository;
        this.accountTradeLockService = accountTradeLockService;
        this.positionRepository = positionRepository;
        this.properties = properties;
    }

    @Scheduled(fixedRate = 60_000)
    public void applyDailyRolloverIfDue() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        LocalTime rolloverTime = properties.getRolloverTime();
        if (rolloverTime != null
                && !now.isBefore(rolloverTime)
                && !today.equals(lastAutomaticRolloverDate)) {
            applyRollover(1);
            lastAutomaticRolloverDate = today;
        }
    }

    @Transactional
    public SwapRolloverResponse applyRollover(int days) {
        if (!properties.isEnabled()) {
            return new SwapRolloverResponse(normalizeDays(days), 0, BigDecimal.ZERO, LocalDateTime.now());
        }
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> applyRolloverLocked(normalizeDays(days))
        );
    }

    private SwapRolloverResponse applyRolloverLocked(int days) {
        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        List<Position> positions = positionRepository.findByAccountIdAndStatusOrderByOpenedAtAsc(
                account.getId(),
                PositionStatus.OPEN
        );
        BigDecimal totalAccruedSwap = BigDecimal.ZERO;
        int appliedPositions = 0;

        for (Position position : positions) {
            BigDecimal dailyRate = dailyRate(position);
            if (dailyRate == null) {
                continue;
            }
            BigDecimal accruedSwap = calculateSwap(position.getQuantity(), dailyRate, days);
            BigDecimal currentSwap = position.getAccruedSwap() == null ? BigDecimal.ZERO : position.getAccruedSwap();
            position.setAccruedSwap(currentSwap.add(accruedSwap).setScale(SWAP_SCALE, RoundingMode.HALF_UP));
            totalAccruedSwap = totalAccruedSwap.add(accruedSwap);
            appliedPositions++;
        }

        positionRepository.saveAll(positions);
        return new SwapRolloverResponse(
                days,
                appliedPositions,
                totalAccruedSwap.setScale(SWAP_SCALE, RoundingMode.HALF_UP),
                LocalDateTime.now()
        );
    }

    private BigDecimal dailyRate(Position position) {
        SwapProperties.SwapRate rate = properties.getRates().get(position.getCurrencyPair());
        if (rate == null) {
            return null;
        }
        return position.getSide() == PositionSide.LONG ? rate.getLongRate() : rate.getShortRate();
    }

    private BigDecimal calculateSwap(BigDecimal quantity, BigDecimal dailyRate, int days) {
        if (quantity == null || dailyRate == null) {
            return BigDecimal.ZERO;
        }
        return quantity
                .divide(PER_TEN_THOUSAND, SWAP_SCALE, RoundingMode.HALF_UP)
                .multiply(dailyRate)
                .multiply(BigDecimal.valueOf(days))
                .setScale(SWAP_SCALE, RoundingMode.HALF_UP);
    }

    private int normalizeDays(int days) {
        return Math.max(1, days);
    }
}
