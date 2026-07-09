package com.example.fx.demo.backend.market.swap;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.common.enums.PositionSide;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import com.example.fx.demo.backend.position.domain.Position;
import com.example.fx.demo.backend.position.repository.PositionRepository;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwapRolloverServiceTest {

    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private SwapRolloverService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        SwapProperties properties = new SwapProperties();
        SwapProperties.SwapRate usdJpy = new SwapProperties.SwapRate();
        usdJpy.setLongRate(new BigDecimal("20"));
        usdJpy.setShortRate(new BigDecimal("-60"));
        properties.setRates(Map.of("USD/JPY", usdJpy));

        service = new SwapRolloverService(
                accountRepository,
                new AccountTradeLockService(),
                positionRepository,
                properties
        );
    }

    @Test
    void appliesConfiguredSwapRatesToOpenPositions() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", 1L);
        account.setAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER);
        Position longPosition = position(10L, PositionSide.LONG, "10000");
        Position shortPosition = position(11L, PositionSide.SHORT, "20000");

        when(accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(account));
        when(positionRepository.findByAccountIdAndStatusOrderByOpenedAtAsc(1L, PositionStatus.OPEN))
                .thenReturn(List.of(longPosition, shortPosition));
        when(positionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        SwapRolloverResponse response = service.applyRollover(1);

        assertThat(longPosition.getAccruedSwap()).isEqualByComparingTo(new BigDecimal("20.0000"));
        assertThat(shortPosition.getAccruedSwap()).isEqualByComparingTo(new BigDecimal("-120.0000"));
        assertThat(response.appliedPositions()).isEqualTo(2);
        assertThat(response.totalAccruedSwap()).isEqualByComparingTo(new BigDecimal("-100.0000"));
    }

    private Position position(Long id, PositionSide side, String quantity) {
        Position position = new Position();
        ReflectionTestUtils.setField(position, "id", id);
        position.setAccountId(1L);
        position.setCurrencyPair("USD/JPY");
        position.setSide(side);
        position.setQuantity(new BigDecimal(quantity));
        position.setStatus(PositionStatus.OPEN);
        position.setAccruedSwap(BigDecimal.ZERO);
        return position;
    }
}
