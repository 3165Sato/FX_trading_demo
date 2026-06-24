package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.account.Account;
import com.example.fx.demo.backend.account.AccountRepository;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.TriggerOrderStatus;
import com.example.fx.demo.backend.market.CurrencyPair;
import com.example.fx.demo.backend.market.CurrencyPairRepository;
import com.example.fx.demo.backend.market.MarketRate;
import com.example.fx.demo.backend.market.MarketRateRepository;
import com.example.fx.demo.backend.order.dto.PendingOrderRequest;
import com.example.fx.demo.backend.order.dto.PendingOrderResponse;
import com.example.fx.demo.backend.trade.AccountTradeLockService;
import com.example.fx.demo.backend.trade.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.TradeExecutionService;
import com.example.fx.demo.backend.trade.dto.OrderResultResponse;
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
public class TriggerOrderService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AccountRepository accountRepository;
    private final AccountTradeLockService accountTradeLockService;
    private final CurrencyPairRepository currencyPairRepository;
    private final MarketRateRepository marketRateRepository;
    private final TradeExecutionService tradeExecutionService;
    private final TriggerOrderRepository triggerOrderRepository;

    public TriggerOrderService(
            AccountRepository accountRepository,
            AccountTradeLockService accountTradeLockService,
            CurrencyPairRepository currencyPairRepository,
            MarketRateRepository marketRateRepository,
            TradeExecutionService tradeExecutionService,
            TriggerOrderRepository triggerOrderRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountTradeLockService = accountTradeLockService;
        this.currencyPairRepository = currencyPairRepository;
        this.marketRateRepository = marketRateRepository;
        this.tradeExecutionService = tradeExecutionService;
        this.triggerOrderRepository = triggerOrderRepository;
    }

    @Transactional
    public PendingOrderResponse placePendingOrder(PendingOrderRequest request) {
        validateBasicRequest(request);
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
        BigDecimal triggerPrice = request.triggerPrice().setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);
        validateTriggerDirection(request.side(), request.orderType(), triggerPrice, marketRate);

        TriggerOrder order = new TriggerOrder();
        order.setAccountId(account.getId());
        order.setCurrencyPair(currencyPair.getSymbol());
        order.setSide(request.side());
        order.setOrderType(request.orderType());
        order.setQuantity(quantity);
        order.setTriggerPrice(triggerPrice);
        order.setStatus(TriggerOrderStatus.PENDING);
        return toResponse(triggerOrderRepository.save(order));
    }

    @Transactional
    public PendingOrderResponse cancelPendingOrder(Long id) {
        TriggerOrder order = triggerOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending order not found: " + id));
        if (order.getStatus() != TriggerOrderStatus.PENDING && order.getStatus() != TriggerOrderStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be cancelled");
        }
        order.setStatus(TriggerOrderStatus.CANCELLED);
        return toResponse(triggerOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<PendingOrderResponse> listPendingOrders(String status, String currencyPair, Integer limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit));
        TriggerOrderStatus parsedStatus = parseStatus(status);
        List<TriggerOrder> orders;
        if (parsedStatus != null && currencyPair != null && !currencyPair.isBlank()) {
            orders = triggerOrderRepository.findByCurrencyPairAndStatusOrderByCreatedAtDesc(currencyPair, parsedStatus, page);
        } else if (parsedStatus != null) {
            orders = triggerOrderRepository.findByStatusOrderByCreatedAtDesc(parsedStatus, page);
        } else if (currencyPair != null && !currencyPair.isBlank()) {
            orders = triggerOrderRepository.findByCurrencyPairOrderByCreatedAtDesc(currencyPair, page);
        } else {
            orders = triggerOrderRepository.findAllByOrderByCreatedAtDesc(page);
        }
        return orders.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingOrderIds() {
        return triggerOrderRepository.findByStatusInOrderByCreatedAtAsc(List.of(
                        TriggerOrderStatus.PENDING,
                        TriggerOrderStatus.WAITING
                )).stream()
                .map(TriggerOrder::getId)
                .toList();
    }

    @Transactional
    public void evaluatePendingOrder(Long id) {
        accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> {
                    evaluatePendingOrderLocked(id);
                    return null;
                }
        );
    }

    private void evaluatePendingOrderLocked(Long id) {
        TriggerOrder order = triggerOrderRepository.findById(id).orElse(null);
        if (order == null || (order.getStatus() != TriggerOrderStatus.PENDING && order.getStatus() != TriggerOrderStatus.WAITING)) {
            return;
        }
        CurrencyPair currencyPair = currencyPairRepository.findBySymbol(order.getCurrencyPair())
                .filter(pair -> Boolean.TRUE.equals(pair.getEnabled()))
                .orElse(null);
        if (currencyPair == null) {
            reject(order, "Currency pair is no longer available");
            return;
        }
        MarketRate marketRate = marketRateRepository.findByCurrencyPair(currencyPair).orElse(null);
        if (marketRate == null || !isTriggered(order, marketRate)) {
            return;
        }

        try {
            OrderResultResponse result = tradeExecutionService.executeTriggeredOrder(
                    order.getCurrencyPair(),
                    order.getSide(),
                    order.getQuantity(),
                    order.getOrderType()
            );
            order.setStatus(TriggerOrderStatus.TRIGGERED);
            order.setTriggeredAt(LocalDateTime.now());
            order.setResultingOrderId(result.order().id());
            triggerOrderRepository.save(order);
        } catch (ResponseStatusException exception) {
            reject(order, exception.getReason() == null ? "Trigger execution rejected" : exception.getReason());
        }
    }

    private void reject(TriggerOrder order, String reason) {
        order.setStatus(TriggerOrderStatus.REJECTED);
        order.setTriggeredAt(LocalDateTime.now());
        order.setRejectionReason(reason);
        triggerOrderRepository.save(order);
    }

    private boolean isTriggered(TriggerOrder order, MarketRate marketRate) {
        BigDecimal price = executionSidePrice(order.getSide(), marketRate);
        if (price == null) {
            return false;
        }
        return switch (order.getOrderType()) {
            case LIMIT -> order.getSide() == OrderSide.BUY
                    ? price.compareTo(order.getTriggerPrice()) <= 0
                    : price.compareTo(order.getTriggerPrice()) >= 0;
            case STOP -> order.getSide() == OrderSide.BUY
                    ? price.compareTo(order.getTriggerPrice()) >= 0
                    : price.compareTo(order.getTriggerPrice()) <= 0;
            case MARKET -> false;
        };
    }

    private void validateBasicRequest(PendingOrderRequest request) {
        if (request.currencyPair() == null || request.currencyPair().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "通貨ペアを選択してください。");
        }
        if (request.side() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "売買区分を選択してください。");
        }
        if (request.orderType() != OrderType.LIMIT && request.orderType() != OrderType.STOP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "注文種別は指値または逆指値を選択してください。");
        }
        if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数量は0より大きい値を入力してください。");
        }
        if (request.triggerPrice() == null || request.triggerPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "注文価格は0より大きい値を入力してください。");
        }
    }

    private void validateTriggerDirection(
            OrderSide side,
            OrderType orderType,
            BigDecimal triggerPrice,
            MarketRate marketRate
    ) {
        BigDecimal referencePrice = executionSidePrice(side, marketRate);
        boolean valid = switch (orderType) {
            case LIMIT -> side == OrderSide.BUY
                    ? triggerPrice.compareTo(referencePrice) < 0
                    : triggerPrice.compareTo(referencePrice) > 0;
            case STOP -> side == OrderSide.BUY
                    ? triggerPrice.compareTo(referencePrice) > 0
                    : triggerPrice.compareTo(referencePrice) < 0;
            case MARKET -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    buildInvalidTriggerPriceMessage(side, orderType, referencePrice)
            );
        }
    }

    private String buildInvalidTriggerPriceMessage(OrderSide side, OrderType orderType, BigDecimal referencePrice) {
        String referenceLabel = side == OrderSide.BUY ? "Ask" : "Bid";
        String direction = switch (orderType) {
            case LIMIT -> side == OrderSide.BUY ? "低い価格" : "高い価格";
            case STOP -> side == OrderSide.BUY ? "高い価格" : "低い価格";
            case MARKET -> "有効な価格";
        };
        String orderTypeLabel = orderType == OrderType.LIMIT ? "指値" : "逆指値";
        String sideLabel = side == OrderSide.BUY ? "買い" : "売り";
        return sideLabel + orderTypeLabel
                + "の注文価格が現在価格に対して不正です。現在の" + referenceLabel
                + "（" + referencePrice + "）より" + direction + "を指定してください。";
    }

    private BigDecimal executionSidePrice(OrderSide side, MarketRate marketRate) {
        return side == OrderSide.BUY ? marketRate.getAsk() : marketRate.getBid();
    }

    private TriggerOrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return TriggerOrderStatus.PENDING;
        }
        try {
            return TriggerOrderStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown pending order status: " + status);
        }
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

    private PendingOrderResponse toResponse(TriggerOrder order) {
        return new PendingOrderResponse(
                order.getId(),
                order.getCurrencyPair(),
                order.getSide().name(),
                order.getOrderType().name(),
                order.getQuantity(),
                order.getTriggerPrice(),
                order.getStatus().name(),
                order.getCreatedAt(),
                order.getTriggeredAt(),
                order.getResultingOrderId(),
                order.getRejectionReason()
        );
    }
}
