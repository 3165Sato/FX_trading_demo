package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.account.Account;
import com.example.fx.demo.backend.account.AccountRepository;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.market.CurrencyPair;
import com.example.fx.demo.backend.market.CurrencyPairRepository;
import com.example.fx.demo.backend.market.MarketRate;
import com.example.fx.demo.backend.market.MarketRateRepository;
import com.example.fx.demo.backend.margin.CurrencyConverter;
import com.example.fx.demo.backend.margin.MarginRule;
import com.example.fx.demo.backend.margin.MarginRuleRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class PositionService {

    private static final BigDecimal DEFAULT_LEVERAGE = new BigDecimal("25");
    private static final int MARGIN_SCALE = 8;

    private final AccountRepository accountRepository;
    private final CurrencyPairRepository currencyPairRepository;
    private final MarketRateRepository marketRateRepository;
    private final MarginRuleRepository marginRuleRepository;
    private final TradeRepository tradeRepository;
    private final CurrencyConverter currencyConverter;
    private final PositionNettingCalculator calculator = new PositionNettingCalculator();
    private final PositionValuationCalculator valuationCalculator = new PositionValuationCalculator();

    public PositionService(
            AccountRepository accountRepository,
            CurrencyPairRepository currencyPairRepository,
            MarketRateRepository marketRateRepository,
            MarginRuleRepository marginRuleRepository,
            TradeRepository tradeRepository,
            CurrencyConverter currencyConverter
    ) {
        this.accountRepository = accountRepository;
        this.currencyPairRepository = currencyPairRepository;
        this.marketRateRepository = marketRateRepository;
        this.marginRuleRepository = marginRuleRepository;
        this.tradeRepository = tradeRepository;
        this.currencyConverter = currencyConverter;
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(String currencyPair) {
        PositionCalculationResult result = calculatePositions(currencyPair);
        Map<String, MarketRate> rates = loadRates();
        Map<String, CurrencyPairScale> scales = loadScales();
        Map<String, MarginRule> marginRules = loadMarginRules();
        Map<String, BigDecimal> midRates = toMidRateMap(rates);

        return result.openPositions().stream()
                .map(position -> toResponse(
                        position,
                        rates.get(position.currencyPair()),
                        scales.get(position.currencyPair()),
                        marginRules.get(position.currencyPair()),
                        midRates
                ))
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

    @Transactional(readOnly = true)
    public BigDecimal calculateProjectedUsedMargin(
            String currencyPair,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal executionPrice
    ) {
        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        List<PositionTradeInput> inputs = tradeRepository.findByAccountIdOrderByExecutedAtAsc(account.getId()).stream()
                .map(this::toInput)
                .collect(Collectors.toList());
        inputs.add(new PositionTradeInput(currencyPair, side, quantity, executionPrice, LocalDateTime.now()));

        PositionCalculationResult projected = calculator.calculate(inputs, loadScales());
        return calculateUsedMargin(projected.openPositions());
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
            CurrencyPairScale scale,
            MarginRule marginRule,
            Map<String, BigDecimal> midRates
    ) {
        BigDecimal requiredMargin = calculateRequiredMargin(position, marketRate, marginRule, midRates);
        return valuationCalculator.toResponse(
                position,
                marketRate == null ? null : marketRate.getBid(),
                marketRate == null ? null : marketRate.getAsk(),
                scale,
                requiredMargin
        );
    }

    private BigDecimal calculateRequiredMargin(
            PositionSnapshot position,
            MarketRate marketRate,
            MarginRule marginRule,
            Map<String, BigDecimal> midRates
    ) {
        if (marketRate == null || marketRate.getMidPrice() == null) {
            return null;
        }
        BigDecimal leverage = marginRule != null && marginRule.getLeverage() != null
                ? marginRule.getLeverage()
                : DEFAULT_LEVERAGE;
        if (leverage.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal notionalQuote = position.quantity().multiply(marketRate.getMidPrice());
        BigDecimal requiredMarginQuote = notionalQuote.divide(leverage, MARGIN_SCALE, RoundingMode.HALF_UP);
        return currencyConverter.toJpy(requiredMarginQuote, position.quoteCurrency(), midRates);
    }

    private BigDecimal calculateUsedMargin(List<PositionSnapshot> positions) {
        Map<String, MarketRate> rates = loadRates();
        Map<String, MarginRule> marginRules = loadMarginRules();
        Map<String, BigDecimal> midRates = toMidRateMap(rates);

        BigDecimal total = BigDecimal.ZERO;
        for (PositionSnapshot position : positions) {
            BigDecimal requiredMargin = calculateRequiredMargin(
                    position,
                    rates.get(position.currencyPair()),
                    marginRules.get(position.currencyPair()),
                    midRates
            );
            if (requiredMargin == null) {
                return null;
            }
            total = total.add(requiredMargin);
        }
        return total.setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateUnrealizedPnl(
            PositionSnapshot position,
            MarketRate marketRate,
            CurrencyPairScale scale
    ) {
        return valuationCalculator.calculateUnrealizedPnl(
                position,
                marketRate == null ? null : "LONG".equals(position.side()) ? marketRate.getBid() : marketRate.getAsk(),
                scale
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

    private Map<String, MarginRule> loadMarginRules() {
        return marginRuleRepository.findAll().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .collect(Collectors.toMap(
                        MarginRule::getCurrencyPair,
                        rule -> rule,
                        (first, ignored) -> first
                ));
    }

    private Map<String, BigDecimal> toMidRateMap(Map<String, MarketRate> rates) {
        return rates.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getMidPrice(),
                        (first, ignored) -> first
                ));
    }
}
