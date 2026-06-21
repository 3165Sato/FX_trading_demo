package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.account.Account;
import com.example.fx.demo.backend.account.AccountRepository;
import com.example.fx.demo.backend.market.CurrencyPair;
import com.example.fx.demo.backend.market.CurrencyPairRepository;
import com.example.fx.demo.backend.market.MarketRate;
import com.example.fx.demo.backend.market.MarketRateRepository;
import com.example.fx.demo.backend.position.dto.PnlSummaryResponse;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import com.example.fx.demo.backend.trade.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.Trade;
import com.example.fx.demo.backend.trade.TradeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class PositionService {

    private final AccountRepository accountRepository;
    private final CurrencyPairRepository currencyPairRepository;
    private final MarketRateRepository marketRateRepository;
    private final TradeRepository tradeRepository;
    private final PositionNettingCalculator calculator = new PositionNettingCalculator();

    public PositionService(
            AccountRepository accountRepository,
            CurrencyPairRepository currencyPairRepository,
            MarketRateRepository marketRateRepository,
            TradeRepository tradeRepository
    ) {
        this.accountRepository = accountRepository;
        this.currencyPairRepository = currencyPairRepository;
        this.marketRateRepository = marketRateRepository;
        this.tradeRepository = tradeRepository;
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(String currencyPair) {
        PositionCalculationResult result = calculatePositions(currencyPair);
        Map<String, MarketRate> rates = loadRates();
        Map<String, CurrencyPairScale> scales = loadScales();

        return result.openPositions().stream()
                .map(position -> toResponse(position, rates.get(position.currencyPair()), scales.get(position.currencyPair())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PnlSummaryResponse getPnlSummary() {
        PositionCalculationResult result = calculatePositions(null);
        Map<String, MarketRate> rates = loadRates();
        Map<String, CurrencyPairScale> scales = loadScales();
        Map<String, BigDecimal> unrealizedByCurrency = new TreeMap<>();

        for (PositionSnapshot position : result.openPositions()) {
            CurrencyPairScale scale = scales.get(position.currencyPair());
            BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, rates.get(position.currencyPair()), scale);
            if (unrealizedPnl == null) {
                continue;
            }
            unrealizedByCurrency.merge(position.quoteCurrency(), unrealizedPnl, BigDecimal::add);
        }

        return new PnlSummaryResponse(unrealizedByCurrency, result.realizedByCurrency());
    }

    private PositionCalculationResult calculatePositions(String currencyPair) {
        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        List<Trade> trades = currencyPair == null || currencyPair.isBlank()
                ? tradeRepository.findByAccountIdOrderByExecutedAtAsc(account.getId())
                : tradeRepository.findByAccountIdAndCurrencyPairOrderByExecutedAtAsc(account.getId(), currencyPair);
        return calculator.calculate(trades.stream().map(this::toInput).toList(), loadScales());
    }

    private PositionResponse toResponse(
            PositionSnapshot position,
            MarketRate marketRate,
            CurrencyPairScale scale
    ) {
        BigDecimal currentPrice = currentPrice(position, marketRate);
        BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, marketRate, scale);
        return new PositionResponse(
                position.currencyPair(),
                position.side(),
                position.quantity(),
                position.averagePrice(),
                position.quoteCurrency(),
                currentPrice,
                unrealizedPnl,
                position.updatedAt()
        );
    }

    private BigDecimal calculateUnrealizedPnl(
            PositionSnapshot position,
            MarketRate marketRate,
            CurrencyPairScale scale
    ) {
        BigDecimal currentPrice = currentPrice(position, marketRate);
        if (currentPrice == null || scale == null) {
            return null;
        }
        BigDecimal pnl = "LONG".equals(position.side())
                ? currentPrice.subtract(position.averagePrice()).multiply(position.quantity())
                : position.averagePrice().subtract(currentPrice).multiply(position.quantity());
        return pnl.setScale(scale.pnlScale(), RoundingMode.HALF_UP);
    }

    private BigDecimal currentPrice(PositionSnapshot position, MarketRate marketRate) {
        if (marketRate == null) {
            return null;
        }
        return "LONG".equals(position.side()) ? marketRate.getBid() : marketRate.getAsk();
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
                        pair -> new CurrencyPairScale(pair.getQuoteCurrency(), pair.getPriceScale(), pair.getQuantityScale()),
                        (first, ignored) -> first
                ));
    }

    private Map<String, MarketRate> loadRates() {
        return marketRateRepository.findByCurrencyPair_EnabledTrue().stream()
                .collect(Collectors.toMap(
                        rate -> rate.getCurrencyPair().getSymbol(),
                        rate -> rate,
                        (first, ignored) -> first
                ));
    }
}
