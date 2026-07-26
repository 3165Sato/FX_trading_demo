package com.example.fx.demo.backend.position.service;

import com.example.fx.demo.backend.position.domain.Position;
import com.example.fx.demo.backend.position.repository.PositionRepository;
import com.example.fx.demo.backend.position.model.CurrencyPairScale;
import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.common.enums.ExitOrderType;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.common.enums.OrderStatus;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.PositionSide;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import com.example.fx.demo.backend.common.enums.SwapRealizationSource;
import com.example.fx.demo.backend.common.enums.TradeKind;
import com.example.fx.demo.backend.common.enums.TriggerOrderPurpose;
import com.example.fx.demo.backend.common.enums.TriggerOrderStatus;
import com.example.fx.demo.backend.market.pair.CurrencyPair;
import com.example.fx.demo.backend.market.pair.CurrencyPairRepository;
import com.example.fx.demo.backend.market.rate.MarketRate;
import com.example.fx.demo.backend.market.rate.MarketRateRepository;
import com.example.fx.demo.backend.margin.service.CurrencyConverter;
import com.example.fx.demo.backend.margin.policy.MarginAggregationPolicy;
import com.example.fx.demo.backend.margin.domain.MarginRule;
import com.example.fx.demo.backend.margin.repository.MarginRuleRepository;
import com.example.fx.demo.backend.margin.config.MarginProperties;
import com.example.fx.demo.backend.margin.policy.MarginRateEvaluationPolicy;
import com.example.fx.demo.backend.order.domain.FxOrder;
import com.example.fx.demo.backend.order.repository.FxOrderRepository;
import com.example.fx.demo.backend.order.domain.TriggerOrder;
import com.example.fx.demo.backend.order.repository.TriggerOrderRepository;
import com.example.fx.demo.backend.position.domain.SwapRealization;
import com.example.fx.demo.backend.position.dto.PnlSummaryResponse;
import com.example.fx.demo.backend.position.dto.PositionCloseResponse;
import com.example.fx.demo.backend.position.dto.PositionExitOrderResponse;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import com.example.fx.demo.backend.position.repository.SwapRealizationRepository;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.domain.Trade;
import com.example.fx.demo.backend.trade.repository.TradeRepository;
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
import java.util.ArrayList;
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
    private final List<MarginAggregationPolicy> marginAggregationPolicies;
    private final MarginProperties marginProperties;
    private final List<MarginRateEvaluationPolicy> marginRateEvaluationPolicies;
    private final MarginRuleRepository marginRuleRepository;
    private final MarketRateRepository marketRateRepository;
    private final PositionRepository positionRepository;
    private final SwapRealizationRepository swapRealizationRepository;
    private final TradeRepository tradeRepository;
    private final TriggerOrderRepository triggerOrderRepository;

    public PositionService(
            AccountRepository accountRepository,
            AccountTradeLockService accountTradeLockService,
            CurrencyPairRepository currencyPairRepository,
            CurrencyConverter currencyConverter,
            FxOrderRepository fxOrderRepository,
            List<MarginAggregationPolicy> marginAggregationPolicies,
            MarginProperties marginProperties,
            List<MarginRateEvaluationPolicy> marginRateEvaluationPolicies,
            MarginRuleRepository marginRuleRepository,
            MarketRateRepository marketRateRepository,
            PositionRepository positionRepository,
            SwapRealizationRepository swapRealizationRepository,
            TradeRepository tradeRepository,
            TriggerOrderRepository triggerOrderRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountTradeLockService = accountTradeLockService;
        this.currencyPairRepository = currencyPairRepository;
        this.currencyConverter = currencyConverter;
        this.fxOrderRepository = fxOrderRepository;
        this.marginAggregationPolicies = marginAggregationPolicies;
        this.marginProperties = marginProperties;
        this.marginRateEvaluationPolicies = marginRateEvaluationPolicies;
        this.marginRuleRepository = marginRuleRepository;
        this.marketRateRepository = marketRateRepository;
        this.positionRepository = positionRepository;
        this.swapRealizationRepository = swapRealizationRepository;
        this.tradeRepository = tradeRepository;
        this.triggerOrderRepository = triggerOrderRepository;
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
        Map<String, MarketRate> rates = loadRates();
        Map<String, BigDecimal> midRates = loadMidRates(rates);
        Map<String, BigDecimal> leverageByPair = loadMarginRules();
        Map<Long, List<PositionExitOrderResponse>> exitOrdersByPosition = loadExitOrders(positions);
        return positions.stream()
                .map(position -> toResponse(
                        position,
                        scales.get(position.getCurrencyPair()),
                        rates.get(position.getCurrencyPair()),
                        leverageByPair.getOrDefault(position.getCurrencyPair(), DEFAULT_LEVERAGE),
                        midRates,
                        exitOrdersByPosition.getOrDefault(position.getId(), List.of())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PnlSummaryResponse getPnlSummary() {
        Account account = defaultAccount();
        Map<String, BigDecimal> realizedByCurrency = new TreeMap<>();
        if (account.getRealizedPnl() != null && account.getRealizedPnl().signum() != 0) {
            realizedByCurrency.put("JPY", account.getRealizedPnl().setScale(0, RoundingMode.HALF_UP));
        }
        Map<String, CurrencyPairScale> scales = loadScales();
        Map<String, MarketRate> rates = loadRates();
        Map<String, BigDecimal> unrealizedByCurrency = new TreeMap<>();

        for (Position position : positionRepository.findByAccountIdAndStatusOrderByOpenedAtAsc(
                account.getId(),
                PositionStatus.OPEN
        )) {
            CurrencyPairScale scale = scales.get(position.getCurrencyPair());
            BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, rates.get(position.getCurrencyPair()), scale);
            if (unrealizedPnl == null) {
                continue;
            }
            String quoteCurrency = scale == null ? null : scale.quoteCurrency();
            if (quoteCurrency != null) {
                unrealizedByCurrency.merge(quoteCurrency, unrealizedPnl, BigDecimal::add);
            }
        }

        return new PnlSummaryResponse(unrealizedByCurrency, realizedByCurrency);
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

        Map<String, MarketRate> rates = loadRates();
        Map<String, BigDecimal> midRates = loadMidRates(rates);
        Map<String, BigDecimal> leverageByPair = loadMarginRules();
        BigDecimal usedMargin = calculateUsedMargin(positions, rates, leverageByPair, midRates);
        BigDecimal additionalMargin = calculateRequiredMargin(
                currencyPair,
                quantity,
                executionPrice,
                leverageByPair.getOrDefault(currencyPair, DEFAULT_LEVERAGE),
                midRates
        );
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
        position.setAccruedSwap(BigDecimal.ZERO);
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
        return closePositionForLockedAccount(id, source, null);
    }

    @Transactional
    public PositionCloseResponse closePositionForLockedAccount(Long id, OrderSource source, BigDecimal requestedClosePrice) {
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
        BigDecimal rawClosePrice = requestedClosePrice == null
                ? executionPrice(closeSide, marketRate)
                : requestedClosePrice;
        BigDecimal closePrice = rawClosePrice
                .setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);
        if (closePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "決済価格は0より大きい値を指定してください。");
        }
        BigDecimal realizedPnl = calculateRealizedPnl(position, closePrice)
                .setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);
        BigDecimal realizedSwap = position.getAccruedSwap() == null
                ? BigDecimal.ZERO
                : position.getAccruedSwap().setScale(0, RoundingMode.HALF_UP);

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
        position.setAccruedSwap(BigDecimal.ZERO);
        Position savedPosition = positionRepository.save(position);

        reflectRealizedPnl(account, realizedPnl, currencyPair.getQuoteCurrency(), realizedSwap);
        recordRealizedSwap(account, savedPosition, realizedSwap, SwapRealizationSource.CLOSE, now);
        expirePendingExitOrders(savedPosition.getId());

        return new PositionCloseResponse(
                savedPosition.getId(),
                savedPosition.getCurrencyPair(),
                savedPosition.getSide().name(),
                savedPosition.getQuantity(),
                closePrice,
                realizedPnl,
                realizedSwap,
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

    private void reflectRealizedPnl(Account account, BigDecimal realizedPnl, String quoteCurrency, BigDecimal realizedSwap) {
        BigDecimal realizedJpy = currencyConverter.toJpy(realizedPnl, quoteCurrency, loadMidRates());
        if (realizedJpy == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "実現損益をJPYへ換算できません。");
        }
        BigDecimal swapJpy = realizedSwap == null ? BigDecimal.ZERO : realizedSwap;
        BigDecimal realizedTotalJpy = realizedJpy.add(swapJpy);
        BigDecimal currentRealized = account.getRealizedPnl() == null ? BigDecimal.ZERO : account.getRealizedPnl();
        BigDecimal currentBalance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        account.setRealizedPnl(currentRealized.add(realizedTotalJpy).setScale(0, RoundingMode.HALF_UP));
        account.setBalance(currentBalance.add(realizedTotalJpy).setScale(0, RoundingMode.HALF_UP));
        accountRepository.save(account);
    }

    private void recordRealizedSwap(
            Account account,
            Position position,
            BigDecimal realizedSwap,
            SwapRealizationSource source,
            LocalDateTime realizedAt
    ) {
        if (realizedSwap == null || realizedSwap.signum() == 0) {
            return;
        }
        SwapRealization realization = new SwapRealization();
        realization.setAccountId(account.getId());
        realization.setPositionId(position.getId());
        realization.setAmount(realizedSwap.setScale(4, RoundingMode.HALF_UP));
        realization.setSource(source);
        realization.setRealizedAt(realizedAt);
        swapRealizationRepository.save(realization);
    }

    private BigDecimal calculateRealizedPnl(Position position, BigDecimal closePrice) {
        if (position.getSide() == PositionSide.LONG) {
            return closePrice.subtract(position.getOpenPrice()).multiply(position.getQuantity());
        }
        return position.getOpenPrice().subtract(closePrice).multiply(position.getQuantity());
    }

    private BigDecimal calculateUsedMargin(
            List<Position> positions,
            Map<String, MarketRate> rates,
            Map<String, BigDecimal> leverageByPair,
            Map<String, BigDecimal> midRates
    ) {
        List<BigDecimal> requiredMargins = new ArrayList<>();
        for (Position position : positions) {
            BigDecimal margin = calculateRequiredMargin(
                    position,
                    rates.get(position.getCurrencyPair()),
                    leverageByPair.getOrDefault(position.getCurrencyPair(), DEFAULT_LEVERAGE),
                    midRates
            );
            if (margin == null) {
                return null;
            }
            requiredMargins.add(margin);
        }
        return marginAggregationPolicy().aggregate(requiredMargins).setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRequiredMargin(String currencyPair, BigDecimal quantity, BigDecimal price) {
        return calculateRequiredMargin(currencyPair, quantity, price, loadMarginRules().getOrDefault(currencyPair, DEFAULT_LEVERAGE), loadMidRates(loadRates()));
    }

    private BigDecimal calculateRequiredMargin(
            String currencyPair,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal leverage,
            Map<String, BigDecimal> midRates
    ) {
        CurrencyPair pair = currencyPairRepository.findBySymbol(currencyPair).orElse(null);
        if (pair == null || quantity == null || price == null) {
            return null;
        }
        if (leverage.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal requiredMarginQuote = quantity.multiply(price).divide(leverage, MARGIN_SCALE, RoundingMode.HALF_UP);
        return currencyConverter.toJpy(requiredMarginQuote, pair.getQuoteCurrency(), midRates);
    }

    private BigDecimal calculateRequiredMargin(
            Position position,
            MarketRate marketRate,
            BigDecimal leverage,
            Map<String, BigDecimal> midRates
    ) {
        return calculateRequiredMargin(
                position.getCurrencyPair(),
                position.getQuantity(),
                marginRateEvaluationPolicy().evaluateRate(position, marketRate),
                leverage,
                midRates
        );
    }

    private PositionResponse toResponse(
            Position position,
            CurrencyPairScale scale,
            MarketRate marketRate,
            BigDecimal leverage,
            Map<String, BigDecimal> midRates,
            List<PositionExitOrderResponse> exitOrders
    ) {
        BigDecimal openPrice = position.getOpenPrice() == null ? position.getAvgPrice() : position.getOpenPrice();
        BigDecimal currentPrice = currentPrice(position, marketRate);
        BigDecimal unrealizedPnl = calculateUnrealizedPnl(position, marketRate, scale);
        BigDecimal requiredMargin = calculateRequiredMargin(position, marketRate, leverage, midRates);
        return new PositionResponse(
                position.getId(),
                position.getCurrencyPair(),
                position.getSide().name(),
                position.getQuantity(),
                openPrice,
                scale == null ? null : scale.quoteCurrency(),
                currentPrice,
                unrealizedPnl,
                position.getAccruedSwap() == null ? BigDecimal.ZERO : position.getAccruedSwap(),
                position.getUpdatedAt(),
                requiredMargin,
                position.getOpenedAt(),
                exitOrders
        );
    }

    private Map<Long, List<PositionExitOrderResponse>> loadExitOrders(List<Position> positions) {
        List<Long> positionIds = positions.stream()
                .map(Position::getId)
                .toList();
        if (positionIds.isEmpty()) {
            return Map.of();
        }
        return triggerOrderRepository.findByTargetPositionIdInAndPurposeOrderByCreatedAtAsc(
                        positionIds,
                        TriggerOrderPurpose.EXIT
                ).stream()
                .collect(Collectors.groupingBy(
                        TriggerOrder::getTargetPositionId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toExitOrderResponse, Collectors.toList())
                ));
    }

    private void expirePendingExitOrders(Long positionId) {
        List<TriggerOrder> exitOrders = triggerOrderRepository.findByTargetPositionIdAndPurposeAndStatusInOrderByCreatedAtAsc(
                positionId,
                TriggerOrderPurpose.EXIT,
                pendingTriggerStatuses()
        );
        for (TriggerOrder exitOrder : exitOrders) {
            exitOrder.setStatus(TriggerOrderStatus.EXPIRED);
            exitOrder.setRejectionReason("対象建玉が決済されたため、未発動の決済注文を失効しました。");
        }
        triggerOrderRepository.saveAll(exitOrders);
    }

    private List<TriggerOrderStatus> pendingTriggerStatuses() {
        return List.of(TriggerOrderStatus.PENDING, TriggerOrderStatus.WAITING);
    }

    private PositionExitOrderResponse toExitOrderResponse(TriggerOrder order) {
        ExitOrderType exitType = order.getExitType();
        return new PositionExitOrderResponse(
                order.getId(),
                exitType == null ? null : exitType.name(),
                order.getTriggerPrice(),
                order.getStatus().name(),
                order.getOcoGroupId(),
                order.getCreatedAt(),
                order.getTriggeredAt(),
                order.getParentOrderId()
        );
    }

    private BigDecimal calculateUnrealizedPnl(Position position, MarketRate marketRate, CurrencyPairScale scale) {
        BigDecimal currentPrice = currentPrice(position, marketRate);
        BigDecimal openPrice = position.getOpenPrice() == null ? position.getAvgPrice() : position.getOpenPrice();
        if (currentPrice == null || openPrice == null || scale == null) {
            return null;
        }
        BigDecimal pnl = position.getSide() == PositionSide.LONG
                ? currentPrice.subtract(openPrice).multiply(position.getQuantity())
                : openPrice.subtract(currentPrice).multiply(position.getQuantity());
        return pnl.setScale(scale.priceScale(), RoundingMode.HALF_UP);
    }

    private BigDecimal currentPrice(Position position, MarketRate marketRate) {
        if (marketRate == null) {
            return null;
        }
        return position.getSide() == PositionSide.LONG ? marketRate.getBid() : marketRate.getAsk();
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

    private Map<String, MarketRate> loadRates() {
        return marketRateRepository.findByCurrencyPair_EnabledTrue().stream()
                .collect(Collectors.toMap(
                        rate -> rate.getCurrencyPair().getSymbol(),
                        rate -> rate,
                        (first, ignored) -> first
                ));
    }

    private Map<String, BigDecimal> loadMidRates() {
        return loadMidRates(loadRates());
    }

    private Map<String, BigDecimal> loadMidRates(Map<String, MarketRate> rates) {
        return rates.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getMidPrice(),
                        (first, ignored) -> first
                ));
    }

    private MarginRateEvaluationPolicy marginRateEvaluationPolicy() {
        String configured = marginProperties.getEvaluation().getRatePolicy();
        return marginRateEvaluationPolicies.stream()
                .filter(policy -> policy.name().equalsIgnoreCase(configured))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Unsupported margin rate policy: " + configured
                ));
    }

    private MarginAggregationPolicy marginAggregationPolicy() {
        String configured = marginProperties.getEvaluation().getAggregationPolicy();
        return marginAggregationPolicies.stream()
                .filter(policy -> policy.name().equalsIgnoreCase(configured))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Unsupported margin aggregation policy: " + configured
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
