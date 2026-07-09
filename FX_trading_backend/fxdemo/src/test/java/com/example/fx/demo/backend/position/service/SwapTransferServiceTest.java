package com.example.fx.demo.backend.position.service;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.common.enums.PositionSide;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SwapTransferServiceTest {

    private Account account;
    private AccountRepository accountRepository;
    private PositionRepository positionRepository;
    private SwapRealizationRepository swapRealizationRepository;
    private SwapTransferService service;

    @BeforeEach
    void setUp() {
        account = new Account();
        ReflectionTestUtils.setField(account, "id", 1L);
        account.setAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER);
        account.setBalance(new BigDecimal("1000000"));
        account.setRealizedPnl(BigDecimal.ZERO);

        accountRepository = mock(AccountRepository.class);
        positionRepository = mock(PositionRepository.class);
        swapRealizationRepository = mock(SwapRealizationRepository.class);
        service = new SwapTransferService(
                accountRepository,
                new AccountTradeLockService(),
                positionRepository,
                swapRealizationRepository
        );

        when(accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER))
                .thenReturn(Optional.of(account));
    }

    @Test
    void transfersPositiveAccruedSwapToBalanceWithoutClosingPosition() {
        Position position = position(10L, "500.0000", PositionStatus.OPEN);
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));

        BigDecimal equityBefore = account.getBalance().add(position.getAccruedSwap());
        PositionSwapTransferResponse response = service.transfer(10L);
        BigDecimal equityAfter = account.getBalance().add(position.getAccruedSwap());

        assertThat(response.transferredSwap()).isEqualByComparingTo(new BigDecimal("500.0000"));
        assertThat(response.balanceAfter()).isEqualByComparingTo(new BigDecimal("1000500"));
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("1000500"));
        assertThat(account.getRealizedPnl()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(position.getAccruedSwap()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(position.getStatus()).isEqualTo(PositionStatus.OPEN);
        assertThat(equityAfter).isEqualByComparingTo(equityBefore);

        ArgumentCaptor<SwapRealization> captor = ArgumentCaptor.forClass(SwapRealization.class);
        verify(swapRealizationRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(SwapRealizationSource.TRANSFER);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("500.0000"));
    }

    @Test
    void transfersNegativeAccruedSwapToBalance() {
        Position position = position(10L, "-300.0000", PositionStatus.OPEN);
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));

        PositionSwapTransferResponse response = service.transfer(10L);

        assertThat(response.transferredSwap()).isEqualByComparingTo(new BigDecimal("-300.0000"));
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("999700"));
        assertThat(account.getRealizedPnl()).isEqualByComparingTo(new BigDecimal("-300"));
        assertThat(position.getAccruedSwap()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    void transferIsIdempotentWhenAccruedSwapIsZero() {
        Position position = position(10L, "0.0000", PositionStatus.OPEN);
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));

        PositionSwapTransferResponse response = service.transfer(10L);

        assertThat(response.transferredSwap()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("1000000"));
        verify(positionRepository, never()).save(position);
        verify(swapRealizationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transfersAllOpenPositionSwaps() {
        Position positive = position(10L, "500.0000", PositionStatus.OPEN);
        Position negative = position(11L, "-300.0000", PositionStatus.OPEN);
        Position zero = position(12L, "0.0000", PositionStatus.OPEN);
        when(positionRepository.findByAccountIdAndStatusOrderByOpenedAtAsc(1L, PositionStatus.OPEN))
                .thenReturn(List.of(positive, negative, zero));

        SwapTransferAllResponse response = service.transferAll();

        assertThat(response.transferredPositions()).isEqualTo(2);
        assertThat(response.totalTransferredSwap()).isEqualByComparingTo(new BigDecimal("200.0000"));
        assertThat(response.balanceAfter()).isEqualByComparingTo(new BigDecimal("1000200"));
        assertThat(positive.getAccruedSwap()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(negative.getAccruedSwap()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(zero.getAccruedSwap()).isEqualByComparingTo(new BigDecimal("0.0000"));
    }

    @Test
    void rejectsClosedPosition() {
        Position position = position(10L, "500.0000", PositionStatus.CLOSED);
        when(positionRepository.findById(10L)).thenReturn(Optional.of(position));

        assertThatThrownBy(() -> service.transfer(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    private Position position(Long id, String accruedSwap, PositionStatus status) {
        Position position = new Position();
        ReflectionTestUtils.setField(position, "id", id);
        position.setAccountId(1L);
        position.setCurrencyPair("USD/JPY");
        position.setSide(PositionSide.LONG);
        position.setQuantity(new BigDecimal("10000"));
        position.setStatus(status);
        position.setAccruedSwap(new BigDecimal(accruedSwap));
        return position;
    }
}
