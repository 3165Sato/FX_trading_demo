package com.example.fx.demo.backend.trade;

import com.example.fx.demo.backend.account.AccountSummaryService;
import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.account.Account;
import com.example.fx.demo.backend.account.AccountRepository;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.common.enums.OrderStatus;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.PositionSide;
import com.example.fx.demo.backend.common.enums.TradeKind;
import com.example.fx.demo.backend.market.CurrencyPair;
import com.example.fx.demo.backend.market.CurrencyPairRepository;
import com.example.fx.demo.backend.market.MarketRate;
import com.example.fx.demo.backend.market.MarketRateRepository;
import com.example.fx.demo.backend.margin.MarginRiskService;
import com.example.fx.demo.backend.order.FxOrder;
import com.example.fx.demo.backend.order.FxOrderRepository;
import com.example.fx.demo.backend.position.PositionService;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import com.example.fx.demo.backend.trade.dto.MarketOrderRequest;
import com.example.fx.demo.backend.trade.dto.OrderResultResponse;
import com.example.fx.demo.backend.trade.dto.OrderSummaryResponse;
import com.example.fx.demo.backend.trade.dto.TradeSummaryResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TradeExecutionService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AccountRepository accountRepository;
    private final CurrencyPairRepository currencyPairRepository;
    private final MarketRateRepository marketRateRepository;
    private final AccountSummaryService accountSummaryService;
    private final FxOrderRepository fxOrderRepository;
    private final MarginRiskService marginRiskService;
    private final PositionService positionService;
    private final TradeRepository tradeRepository;
    private final AccountTradeLockService accountTradeLockService;

    public TradeExecutionService(
            AccountRepository accountRepository,
            CurrencyPairRepository currencyPairRepository,
            MarketRateRepository marketRateRepository,
            AccountSummaryService accountSummaryService,
            FxOrderRepository fxOrderRepository,
            MarginRiskService marginRiskService,
            PositionService positionService,
            TradeRepository tradeRepository,
            AccountTradeLockService accountTradeLockService
    ) {
        this.accountRepository = accountRepository;
        this.currencyPairRepository = currencyPairRepository;
        this.marketRateRepository = marketRateRepository;
        this.accountSummaryService = accountSummaryService;
        this.fxOrderRepository = fxOrderRepository;
        this.marginRiskService = marginRiskService;
        this.positionService = positionService;
        this.tradeRepository = tradeRepository;
        this.accountTradeLockService = accountTradeLockService;
    }

    @Transactional
    public OrderResultResponse placeMarketOrder(MarketOrderRequest request) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> placeMarketOrderLocked(request)
        );
    }

    @Transactional
    public List<OrderResultResponse> liquidateAllPositionsIfMarginRatioAtOrBelow(BigDecimal threshold) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> liquidateAllPositionsIfStillUnsafe(threshold)
        );
    }

    private OrderResultResponse placeMarketOrderLocked(MarketOrderRequest request) {
        validateRequest(request);
        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        CurrencyPair currencyPair = currencyPairRepository.findBySymbol(request.currencyPair())
                .filter(pair -> Boolean.TRUE.equals(pair.getEnabled()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Currency pair not found: " + request.currencyPair()
                ));
        MarketRate marketRate = marketRateRepository.findByCurrencyPair(currencyPair)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Latest market rate is not available: " + currencyPair.getSymbol()
                ));

        BigDecimal quantity = request.quantity().setScale(currencyPair.getQuantityScale(), RoundingMode.HALF_UP);
        BigDecimal executionPrice = executionPrice(request.side(), marketRate)
                .setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);
        marginRiskService.assertSufficientMargin(currencyPair.getSymbol(), request.side(), quantity, executionPrice);

        return executeMarketOrder(account, currencyPair, request.side(), quantity, executionPrice, OrderType.MARKET, OrderSource.MANUAL);
    }

    public OrderResultResponse executeTriggeredOrder(
            String currencyPairSymbol,
            OrderSide side,
            BigDecimal quantity,
            OrderType orderType
    ) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> executeTriggeredOrderLocked(currencyPairSymbol, side, quantity, orderType)
        );
    }

    private OrderResultResponse executeTriggeredOrderLocked(
            String currencyPairSymbol,
            OrderSide side,
            BigDecimal requestedQuantity,
            OrderType orderType
    ) {
        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        CurrencyPair currencyPair = currencyPairRepository.findBySymbol(currencyPairSymbol)
                .filter(pair -> Boolean.TRUE.equals(pair.getEnabled()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Currency pair not found: " + currencyPairSymbol
                ));
        MarketRate marketRate = marketRateRepository.findByCurrencyPair(currencyPair)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Latest market rate is not available: " + currencyPair.getSymbol()
                ));
        BigDecimal quantity = requestedQuantity.setScale(currencyPair.getQuantityScale(), RoundingMode.HALF_UP);
        BigDecimal executionPrice = executionPrice(side, marketRate)
                .setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);
        marginRiskService.assertSufficientMargin(currencyPair.getSymbol(), side, quantity, executionPrice);

        return executeMarketOrder(account, currencyPair, side, quantity, executionPrice, orderType, OrderSource.TRIGGER);
    }

    private List<OrderResultResponse> liquidateAllPositionsIfStillUnsafe(BigDecimal threshold) {
        AccountSummaryResponse summary = accountSummaryService.getDefaultAccountSummary();
        if (summary.marginRatio() == null || summary.marginRatio().compareTo(threshold) > 0) {
            return List.of();
        }

        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        List<OrderResultResponse> results = new java.util.ArrayList<>();
        for (PositionResponse position : positionService.getPositions(null)) {
            results.add(positionService.closePositionForLockedAccount(position.id(), OrderSource.LOSS_CUT).execution());
        }
        return results;
    }

    private OrderResultResponse executeMarketOrder(
            Account account,
            CurrencyPair currencyPair,
            OrderSide side,
            BigDecimal quantity,
            BigDecimal executionPrice,
            OrderType orderType,
            OrderSource source
    ) {
        LocalDateTime now = LocalDateTime.now();

        FxOrder order = new FxOrder();
        order.setAccountId(account.getId());
        order.setCurrencyPair(currencyPair.getSymbol());
        order.setSide(side);
        order.setOrderType(orderType);
        order.setQuantity(quantity);
        order.setOrderPrice(executionPrice);
        // 既存DBのOrderStatus制約に合わせ、即時約定済みの注文はEXECUTEDとして保存する。
        order.setStatus(OrderStatus.EXECUTED);
        order.setSource(source);
        order.setRequestedAt(now);
        order.setExecutedAt(now);
        FxOrder savedOrder = fxOrderRepository.save(order);

        Trade trade = new Trade();
        trade.setOrderId(savedOrder.getId());
        trade.setAccountId(account.getId());
        trade.setCurrencyPair(currencyPair.getSymbol());
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setExecutionPrice(executionPrice);
        trade.setExecutedAt(now);
        trade.setTradeKind(TradeKind.OPEN);
        Trade savedTrade = tradeRepository.save(trade);

        PositionSide positionSide = side == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT;
        var position = positionService.openPosition(
                account,
                currencyPair,
                positionSide,
                quantity,
                executionPrice,
                savedTrade.getId(),
                now
        );
        savedTrade.setPositionId(position.getId());
        savedTrade = tradeRepository.save(savedTrade);

        return new OrderResultResponse(toOrderResponse(savedOrder), toTradeResponse(savedTrade));
    }

    @Transactional(readOnly = true)
    public List<TradeSummaryResponse> getTrades(String currencyPair, Integer limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit));
        List<Trade> trades = currencyPair == null || currencyPair.isBlank()
                ? tradeRepository.findAllByOrderByExecutedAtDesc(page)
                : tradeRepository.findByCurrencyPairOrderByExecutedAtDesc(currencyPair, page);
        return trades.stream().map(this::toTradeResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getOrders(String currencyPair, Integer limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit));
        List<FxOrder> orders = currencyPair == null || currencyPair.isBlank()
                ? fxOrderRepository.findAllByOrderByRequestedAtDesc(page)
                : fxOrderRepository.findByCurrencyPairOrderByRequestedAtDesc(currencyPair, page);
        return orders.stream().map(this::toOrderResponse).toList();
    }

    private void validateRequest(MarketOrderRequest request) {
        if (request.currencyPair() == null || request.currencyPair().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyPair is required");
        }
        if (request.side() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "side is required");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be greater than zero");
        }
    }

    private BigDecimal executionPrice(OrderSide side, MarketRate marketRate) {
        return switch (side) {
            case BUY -> marketRate.getAsk();
            case SELL -> marketRate.getBid();
        };
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
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
