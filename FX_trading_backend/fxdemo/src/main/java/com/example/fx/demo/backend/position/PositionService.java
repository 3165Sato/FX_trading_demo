package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.account.Account;
import com.example.fx.demo.backend.account.AccountRepository;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.common.enums.OrderStatus;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.PositionSide;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import com.example.fx.demo.backend.common.enums.TradeKind;
import com.example.fx.demo.backend.market.CurrencyPair;
import com.example.fx.demo.backend.market.CurrencyPairRepository;
import com.example.fx.demo.backend.market.MarketRate;
import com.example.fx.demo.backend.market.MarketRateRepository;
import com.example.fx.demo.backend.margin.CurrencyConverter;
import com.example.fx.demo.backend.margin.MarginRule;
import com.example.fx.demo.backend.margin.MarginRuleRepository;
import com.example.fx.demo.backend.order.FxOrder;
import com.example.fx.demo.backend.order.FxOrderRepository;
import com.example.fx.demo.backend.position.dto.PnlSummaryResponse;
import com.example.fx.demo.backend.position.dto.PositionCloseResponse;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import com.example.fx.demo.backend.trade.AccountTradeLockService;
import com.example.fx.demo.backend.trade.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.Trade;
import com.example.fx.demo.backend.trade.TradeRepository;
import com.example.fx.demo.backend.trade.dto.OrderResultResponse;
import com.example.fx.demo.backend.trade.dto.OrderSummaryResponse;
import com.example.fx.demo.backend.trade.dto.TradeSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class PositionService {

    private static final BigDecimal DEFAULT_LEVERAGE = new BigDecimal("25");
    private static final int MARGIN_SCALE = 8;

    private final AccountRepository accountRepository;
    private final AccountTradeLockService accountTradeLockService;
    private final CurrencyPairRepository currencyPairRepository;
    private final CurrencyConverter currencyConverter;
    private final FxOrderRepository fxOrderRepository;
    private final MarginRuleRepository marginRuleRepository;
    private final MarketRateRepository marketRateRepository;
    private final PositionRepository positionRepository;
    private final TradeRepository tradeRepository;

    public PositionService(
            AccountRepository accountRepository,
            AccountTradeLockService accountTradeLockService,
            CurrencyPairRepository currencyPairRepository,
            CurrencyConverter currencyConverter,
            FxOrderRepository fxOrderRepository,
            MarginRuleRepository marginRuleRepository,
            MarketRateRepository marketRateRepository,
            PositionRepository positionRepository,
            TradeRepository tradeRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountTradeLockService = accountTradeLockService;
        this.currencyPairRepository = currencyPairRepository;
        this.currencyConverter = currencyConverter;
        this.fxOrderRepository = fxOrderRepository;
        this.marginRuleRepository = marginRuleRepository;
        this.marketRateRepository = marketRateRepository;
        this.positionRepository = positionRepository;
        this.tradeRepository = tradeRepository;
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(String currencyPair) {
        Account account = defaultAccount();
        List<Position> positions = currencyPair == null || currencyPair.isBlank()
                ? positionRepository.findByAccountIdAndStatusOrderByOpenedAtAsc(account.getId(), PositionStatus.OPEN)
                : positionRepository.findByAccountIdAndCurrencyPairAndStatusOrderByOpenedAtAsc(
                        account.getId(),
                        currencyPair,
                        PositionStatus.OPEN
                );
        Map<String, CurrencyPairScale> scales = loadScales();
        return positions.stream()
                .map(position -> toResponse(position, scales.get(position.getCurrencyPair())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PnlSummaryResponse getPnlSummary() {
        Account account = defaultAccount();
        Map<String, BigDecimal> realizedByCurrency = new TreeMap<>();
        if (account.getRealizedPnl() != null && account.getRealizedPnl().signum() != 0) {
            realizedByCurrency.put("JPY", account.getRealizedPnl().setScale(0, RoundingMode.HALF_UP));
        }
        // 1aでは建玉ベースの未実現損益集計は作り込まず、画面上は未評価として扱う。
        return new PnlSummaryResponse(Map.of(), realizedByCurrency);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateProjectedUsedMargin(
            String currencyPair,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal executionPrice
    ) {
        Account account = defaultAccount();
        List<Position> positions = positionRepository.findByAccountIdAndStatusOrderByOpenedAtAsc(
                account.getId(),
                PositionStatus.OPEN
        );

        BigDecimal usedMargin = calculateUsedMargin(positions);
        BigDecimal additionalMargin = calculateRequiredMargin(currencyPair, quantity, executionPrice);
        if (usedMargin == null || additionalMargin == null) {
            return null;
        }
        return usedMargin.add(additionalMargin).setScale(0, RoundingMode.HALF_UP);
    }

    @Transactional
    public Position openPosition(
            Account account,
            CurrencyPair currencyPair,
            PositionSide side,
            BigDecimal quantity,
            BigDecimal openPrice,
            Long openTradeId,
            LocalDateTime openedAt
    ) {
        Position position = new Position();
        position.setAccountId(account.getId());
        position.setCurrencyPair(currencyPair.getSymbol());
        position.setSide(side);
        position.setQuantity(quantity.setScale(currencyPair.getQuantityScale(), RoundingMode.HALF_UP));
        BigDecimal roundedOpenPrice = openPrice.setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);
        position.setOpenPrice(roundedOpenPrice);
        position.setAvgPrice(roundedOpenPrice);
        position.setStatus(PositionStatus.OPEN);
        position.setOpenedAt(openedAt);
        position.setOpenTradeId(openTradeId);
        return positionRepository.save(position);
    }

    @Transactional
    public PositionCloseResponse closePosition(Long id) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> closePositionForLockedAccount(id, OrderSource.MANUAL)
        );
    }

    @Transactional
    public PositionCloseResponse closePositionForLockedAccount(Long id, OrderSource source) {
        Account account = defaultAccount();
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "建玉が見つかりません: " + id));
        if (!account.getId().equals(position.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "建玉が見つかりません: " + id);
        }
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OPEN状態の建玉のみ決済できます。");
        }

        CurrencyPair currencyPair = currencyPairRepository.findBySymbol(position.getCurrencyPair())
                .filter(pair -> Boolean.TRUE.equals(pair.getEnabled()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Currency pair not found: " + position.getCurrencyPair()
                ));
        MarketRate marketRate = marketRateRepository.findByCurrencyPair(currencyPair)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "決済に使う最新レートが取得できません: " + currencyPair.getSymbol()
                ));

        LocalDateTime now = LocalDateTime.now();
        OrderSide closeSide = closeSide(position.getSide());
        BigDecimal closePrice = executionPrice(closeSide, marketRate)
                .setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);
        BigDecimal realizedPnl = calculateRealizedPnl(position, closePrice)
                .setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);

        FxOrder order = createExecutedOrder(account, currencyPair, closeSide, position.getQuantity(), closePrice, source, now);
        Trade trade = createTrade(
                order,
                closeSide,
                position.getQuantity(),
                closePrice,
                now,
                TradeKind.CLOSE,
                position.getId(),
                realizedPnl
        );

        position.setStatus(PositionStatus.CLOSED);
        position.setClosedAt(now);
        position.setCloseTradeId(trade.getId());
        position.setUnrealizedPnl(null);
        Position savedPosition = positionRepository.save(position);

        reflectRealizedPnl(account, realizedPnl, currencyPair.getQuoteCurrency());

        return new PositionCloseResponse(
                savedPosition.getId(),
                savedPosition.getCurrencyPair(),
                savedPosition.getSide().name(),
                savedPosition.getQuantity(),
                closePrice,
                realizedPnl,
                currencyPair.getQuoteCurrency(),
                now,
                new OrderResultResponse(toOrderResponse(order), toTradeResponse(trade))
        );
    }

    private FxOrder createExecutedOrder(
            Account account,
            CurrencyPair currencyPair,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal executionPrice,
            OrderSource source,
            LocalDateTime now
    ) {
        FxOrder order = new FxOrder();
        order.setAccountId(account.getId());
        order.setCurrencyPair(currencyPair.getSymbol());
        order.setSide(side);
        order.setOrderType(OrderType.MARKET);
        order.setQuantity(quantity);
        order.setOrderPrice(executionPrice);
        order.setStatus(OrderStatus.EXECUTED);
        order.setSource(source);
        order.setRequestedAt(now);
        order.setExecutedAt(now);
        return fxOrderRepository.save(order);
    }

    public Trade createTrade(
            FxOrder order,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal executionPrice,
            LocalDateTime executedAt,
            TradeKind tradeKind,
            Long positionId,
            BigDecimal realizedPnl
    ) {
        Trade trade = new Trade();
        trade.setOrderId(order.getId());
        trade.setAccountId(order.getAccountId());
        trade.setCurrencyPair(order.getCurrencyPair());
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setExecutionPrice(executionPrice);
        trade.setExecutedAt(executedAt);
        trade.setTradeKind(tradeKind);
        trade.setPositionId(positionId);
        trade.setRealizedPnl(realizedPnl);
        return tradeRepository.save(trade);
    }

    private void reflectRealizedPnl(Account account, BigDecimal realizedPnl, String quoteCurrency) {
        BigDecimal realizedJpy = currencyConverter.toJpy(realizedPnl, quoteCurrency, loadMidRates());
        if (realizedJpy == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "実現損益をJPYへ換算できません。");
        }
        BigDecimal currentRealized = account.getRealizedPnl() == null ? BigDecimal.ZERO : account.getRealizedPnl();
        BigDecimal currentBalance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        account.setRealizedPnl(currentRealized.add(realizedJpy).setScale(0, RoundingMode.HALF_UP));
        account.setBalance(currentBalance.add(realizedJpy).setScale(0, RoundingMode.HALF_UP));
        accountRepository.save(account);
    }

    private BigDecimal calculateRealizedPnl(Position position, BigDecimal closePrice) {
        if (position.getSide() == PositionSide.LONG) {
            return closePrice.subtract(position.getOpenPrice()).multiply(position.getQuantity());
        }
        return position.getOpenPrice().subtract(closePrice).multiply(position.getQuantity());
    }

    private BigDecimal calculateUsedMargin(List<Position> positions) {
        BigDecimal total = BigDecimal.ZERO;
        for (Position position : positions) {
            BigDecimal margin = calculateRequiredMargin(
                    position.getCurrencyPair(),
                    position.getQuantity(),
                    position.getOpenPrice()
            );
            if (margin == null) {
                return null;
            }
            total = total.add(margin);
        }
        return total.setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRequiredMargin(String currencyPair, BigDecimal quantity, BigDecimal price) {
        CurrencyPair pair = currencyPairRepository.findBySymbol(currencyPair).orElse(null);
        if (pair == null || quantity == null || price == null) {
            return null;
        }
        BigDecimal leverage = loadMarginRules().getOrDefault(currencyPair, DEFAULT_LEVERAGE);
        if (leverage.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal requiredMarginQuote = quantity.multiply(price).divide(leverage, MARGIN_SCALE, RoundingMode.HALF_UP);
        return currencyConverter.toJpy(requiredMarginQuote, pair.getQuoteCurrency(), loadMidRates());
    }

    private PositionResponse toResponse(Position position, CurrencyPairScale scale) {
        BigDecimal openPrice = position.getOpenPrice() == null ? position.getAvgPrice() : position.getOpenPrice();
        return new PositionResponse(
                position.getId(),
                position.getCurrencyPair(),
                position.getSide().name(),
                position.getQuantity(),
                openPrice,
                scale == null ? null : scale.quoteCurrency(),
                null,
                null,
                position.getUpdatedAt(),
                null,
                position.getOpenedAt()
        );
    }

    private Account defaultAccount() {
        return accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
    }

    private OrderSide closeSide(PositionSide side) {
        return side == PositionSide.LONG ? OrderSide.SELL : OrderSide.BUY;
    }

    private BigDecimal executionPrice(OrderSide side, MarketRate marketRate) {
        return switch (side) {
            case BUY -> marketRate.getAsk();
            case SELL -> marketRate.getBid();
        };
    }

    private Map<String, CurrencyPairScale> loadScales() {
        return currencyPairRepository.findAll().stream()
                .collect(Collectors.toMap(
                        CurrencyPair::getSymbol,
                        pair -> new CurrencyPairScale(pair.getQuoteCurrency(), pair.getPriceScale(), pair.getQuantityScale()),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    private Map<String, BigDecimal> loadMarginRules() {
        return marginRuleRepository.findAll().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .collect(Collectors.toMap(
                        MarginRule::getCurrencyPair,
                        rule -> rule.getLeverage() == null ? DEFAULT_LEVERAGE : rule.getLeverage(),
                        (first, ignored) -> first
                ));
    }

    private Map<String, BigDecimal> loadMidRates() {
        return marketRateRepository.findByCurrencyPair_EnabledTrue().stream()
                .collect(Collectors.toMap(
                        rate -> rate.getCurrencyPair().getSymbol(),
                        MarketRate::getMidPrice,
                        (first, ignored) -> first
                ));
    }

    private OrderSummaryResponse toOrderResponse(FxOrder order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCurrencyPair(),
                order.getSide().name(),
                order.getOrderType().name(),
                order.getQuantity(),
                order.getStatus().name(),
                order.getSource() == null ? OrderSource.MANUAL.name() : order.getSource().name(),
                order.getRequestedAt()
        );
    }

    private TradeSummaryResponse toTradeResponse(Trade trade) {
        return new TradeSummaryResponse(
                trade.getId(),
                trade.getOrderId(),
                trade.getCurrencyPair(),
                trade.getSide().name(),
                trade.getQuantity(),
                trade.getExecutionPrice(),
                trade.getExecutedAt(),
                trade.getTradeKind() == null ? TradeKind.OPEN.name() : trade.getTradeKind().name(),
                trade.getPositionId(),
                trade.getRealizedPnl()
        );
    }
}
