package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.account.Account;
import com.example.fx.demo.backend.account.AccountRepository;
import com.example.fx.demo.backend.market.CurrencyPair;
import com.example.fx.demo.backend.market.CurrencyPairRepository;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import com.example.fx.demo.backend.trade.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.Trade;
import com.example.fx.demo.backend.trade.TradeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PositionService {

    private final AccountRepository accountRepository;
    private final CurrencyPairRepository currencyPairRepository;
    private final TradeRepository tradeRepository;
    private final PositionNettingCalculator calculator = new PositionNettingCalculator();

    public PositionService(
            AccountRepository accountRepository,
            CurrencyPairRepository currencyPairRepository,
            TradeRepository tradeRepository
    ) {
        this.accountRepository = accountRepository;
        this.currencyPairRepository = currencyPairRepository;
        this.tradeRepository = tradeRepository;
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(String currencyPair) {
        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        List<Trade> trades = currencyPair == null || currencyPair.isBlank()
                ? tradeRepository.findByAccountIdOrderByExecutedAtAsc(account.getId())
                : tradeRepository.findByAccountIdAndCurrencyPairOrderByExecutedAtAsc(account.getId(), currencyPair);

        return calculator.calculate(
                trades.stream().map(this::toInput).toList(),
                loadScales()
        );
    }

    private PositionTradeInput toInput(Trade trade) {
        return new PositionTradeInput(
                trade.getCurrencyPair(),
                trade.getSide(),
                trade.getQuantity(),
                trade.getExecutionPrice(),
                trade.getExecutedAt()
        );
    }

    private Map<String, CurrencyPairScale> loadScales() {
        return currencyPairRepository.findAll().stream()
                .collect(Collectors.toMap(
                        CurrencyPair::getSymbol,
                        pair -> new CurrencyPairScale(pair.getPriceScale(), pair.getQuantityScale()),
                        (first, ignored) -> first
                ));
    }
}
