package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.account.Account;
import com.example.fx.demo.backend.account.AccountRepository;
import com.example.fx.demo.backend.common.enums.ExitOrderType;
import com.example.fx.demo.backend.common.enums.OrderSide;
import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.common.enums.OrderType;
import com.example.fx.demo.backend.common.enums.PositionSide;
import com.example.fx.demo.backend.common.enums.PositionStatus;
import com.example.fx.demo.backend.common.enums.TriggerOrderPurpose;
import com.example.fx.demo.backend.common.enums.TriggerOrderStatus;
import com.example.fx.demo.backend.market.CurrencyPair;
import com.example.fx.demo.backend.market.CurrencyPairRepository;
import com.example.fx.demo.backend.market.MarketRate;
import com.example.fx.demo.backend.market.MarketRateRepository;
import com.example.fx.demo.backend.order.dto.PendingOrderRequest;
import com.example.fx.demo.backend.order.dto.PendingOrderResponse;
import com.example.fx.demo.backend.order.dto.IfdOrderRequest;
import com.example.fx.demo.backend.order.dto.IfdOrderResponse;
import com.example.fx.demo.backend.position.Position;
import com.example.fx.demo.backend.position.PositionRepository;
import com.example.fx.demo.backend.position.PositionService;
import com.example.fx.demo.backend.position.dto.PositionExitOrderRequest;
import com.example.fx.demo.backend.position.dto.PositionExitOrderResponse;
import com.example.fx.demo.backend.position.dto.PositionOcoOrderRequest;
import com.example.fx.demo.backend.position.dto.PositionOcoOrderResponse;
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
import java.util.Objects;
import java.util.UUID;

@Service
public class TriggerOrderService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AccountRepository accountRepository;
    private final AccountTradeLockService accountTradeLockService;
    private final CurrencyPairRepository currencyPairRepository;
    private final MarketRateRepository marketRateRepository;
    private final PositionRepository positionRepository;
    private final PositionService positionService;
    private final TradeExecutionService tradeExecutionService;
    private final TriggerOrderRepository triggerOrderRepository;

    public TriggerOrderService(
            AccountRepository accountRepository,
            AccountTradeLockService accountTradeLockService,
            CurrencyPairRepository currencyPairRepository,
            MarketRateRepository marketRateRepository,
            PositionRepository positionRepository,
            PositionService positionService,
            TradeExecutionService tradeExecutionService,
            TriggerOrderRepository triggerOrderRepository
    ) {
        this.accountRepository = accountRepository;
        this.accountTradeLockService = accountTradeLockService;
        this.currencyPairRepository = currencyPairRepository;
        this.marketRateRepository = marketRateRepository;
        this.positionRepository = positionRepository;
        this.positionService = positionService;
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
        order.setPurpose(TriggerOrderPurpose.ENTRY);
        return toResponse(triggerOrderRepository.save(order));
    }

    @Transactional
    public IfdOrderResponse placeIfdOrder(IfdOrderRequest request) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> placeIfdOrderLocked(request)
        );
    }

    @Transactional
    public PendingOrderResponse cancelPendingOrder(Long id) {
        TriggerOrder order = triggerOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending order not found: " + id));
        if (order.getOcoGroupId() != null && !order.getOcoGroupId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OCO注文はグループ単位で取消してください。");
        }
        if (order.getStatus() != TriggerOrderStatus.PENDING && order.getStatus() != TriggerOrderStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be cancelled");
        }
        order.setStatus(TriggerOrderStatus.CANCELLED);
        TriggerOrder savedOrder = triggerOrderRepository.save(order);
        cancelIfdChildren(savedOrder);
        return toResponse(savedOrder);
    }

    @Transactional
    public PositionExitOrderResponse placeExitOrder(Long positionId, PositionExitOrderRequest request) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> placeExitOrderLocked(positionId, request)
        );
    }

    @Transactional
    public PositionExitOrderResponse cancelExitOrder(Long positionId, Long exitOrderId) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> cancelExitOrderLocked(positionId, exitOrderId)
        );
    }

    @Transactional
    public PositionOcoOrderResponse placeOcoOrder(Long positionId, PositionOcoOrderRequest request) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> placeOcoOrderLocked(positionId, request)
        );
    }

    @Transactional
    public PositionOcoOrderResponse cancelOcoOrder(Long positionId, String groupId) {
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> cancelOcoOrderLocked(positionId, groupId)
        );
    }

    private PositionExitOrderResponse placeExitOrderLocked(Long positionId, PositionExitOrderRequest request) {
        if (request.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "決済注文の種類をTPまたはSLで指定してください。");
        }
        if (request.triggerPrice() == null || request.triggerPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "決済注文価格は0より大きい値を指定してください。");
        }

        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "対象建玉が見つかりません: " + positionId));
        if (!account.getId().equals(position.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "対象建玉が見つかりません: " + positionId);
        }
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OPEN状態の建玉にのみ決済注文を設定できます。");
        }
        if (triggerOrderRepository.existsByTargetPositionIdAndExitTypeAndStatusIn(
                positionId,
                request.type(),
                pendingStatuses()
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, request.type().name() + "はこの建玉にすでに設定されています。");
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
                        "Latest market rate is not available: " + currencyPair.getSymbol()
                ));
        BigDecimal triggerPrice = request.triggerPrice().setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);
        validateExitDirection(position, request.type(), triggerPrice, marketRate);

        TriggerOrder order = new TriggerOrder();
        order.setAccountId(account.getId());
        order.setCurrencyPair(currencyPair.getSymbol());
        order.setSide(closeSide(position.getSide()));
        order.setOrderType(request.type() == ExitOrderType.TP ? OrderType.LIMIT : OrderType.STOP);
        order.setQuantity(position.getQuantity());
        order.setTriggerPrice(triggerPrice);
        order.setStatus(TriggerOrderStatus.PENDING);
        order.setPurpose(TriggerOrderPurpose.EXIT);
        order.setExitType(request.type());
        order.setTargetPositionId(position.getId());
        return toExitOrderResponse(triggerOrderRepository.save(order));
    }

    private PositionExitOrderResponse cancelExitOrderLocked(Long positionId, Long exitOrderId) {
        TriggerOrder order = triggerOrderRepository.findById(exitOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "決済注文が見つかりません: " + exitOrderId));
        if (order.getPurpose() != TriggerOrderPurpose.EXIT || !positionId.equals(order.getTargetPositionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "決済注文が見つかりません: " + exitOrderId);
        }
        if (order.getStatus() != TriggerOrderStatus.PENDING && order.getStatus() != TriggerOrderStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "未発動の決済注文のみ取消できます。");
        }
        order.setStatus(TriggerOrderStatus.CANCELLED);
        return toExitOrderResponse(triggerOrderRepository.save(order));
    }

    private IfdOrderResponse placeIfdOrderLocked(IfdOrderRequest request) {
        if (request.entry() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IFDの新規注文を指定してください。");
        }
        if (request.exit() == null || request.exit().type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IFDの決済注文はTPまたはSLを1本だけ指定してください。");
        }
        validateBasicRequest(request.entry());
        if (request.exit().triggerPrice() == null || request.exit().triggerPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IFDの決済注文価格は0より大きい値を指定してください。");
        }

        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        CurrencyPair currencyPair = currencyPairRepository.findBySymbol(request.entry().currencyPair())
                .filter(pair -> Boolean.TRUE.equals(pair.getEnabled()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Currency pair not found: " + request.entry().currencyPair()
                ));
        MarketRate marketRate = marketRateRepository.findByCurrencyPair(currencyPair)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Latest market rate is not available: " + currencyPair.getSymbol()
                ));

        BigDecimal quantity = request.entry().quantity().setScale(currencyPair.getQuantityScale(), RoundingMode.HALF_UP);
        BigDecimal entryTriggerPrice = request.entry().triggerPrice().setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP);
        validateTriggerDirection(request.entry().side(), request.entry().orderType(), entryTriggerPrice, marketRate);

        TriggerOrder entryOrder = new TriggerOrder();
        entryOrder.setAccountId(account.getId());
        entryOrder.setCurrencyPair(currencyPair.getSymbol());
        entryOrder.setSide(request.entry().side());
        entryOrder.setOrderType(request.entry().orderType());
        entryOrder.setQuantity(quantity);
        entryOrder.setTriggerPrice(entryTriggerPrice);
        entryOrder.setStatus(TriggerOrderStatus.PENDING);
        entryOrder.setPurpose(TriggerOrderPurpose.ENTRY);
        TriggerOrder savedEntry = triggerOrderRepository.save(entryOrder);

        TriggerOrder exitOrder = new TriggerOrder();
        exitOrder.setAccountId(account.getId());
        exitOrder.setCurrencyPair(currencyPair.getSymbol());
        exitOrder.setSide(closeSide(request.entry().side() == OrderSide.BUY ? PositionSide.LONG : PositionSide.SHORT));
        exitOrder.setOrderType(request.exit().type() == ExitOrderType.TP ? OrderType.LIMIT : OrderType.STOP);
        exitOrder.setQuantity(quantity);
        exitOrder.setTriggerPrice(request.exit().triggerPrice().setScale(currencyPair.getPriceScale(), RoundingMode.HALF_UP));
        exitOrder.setStatus(TriggerOrderStatus.PENDING);
        exitOrder.setPurpose(TriggerOrderPurpose.EXIT);
        exitOrder.setExitType(request.exit().type());
        exitOrder.setParentOrderId(savedEntry.getId());
        TriggerOrder savedExit = triggerOrderRepository.save(exitOrder);

        return new IfdOrderResponse(toResponse(savedEntry), toResponse(savedExit));
    }

    private PositionOcoOrderResponse placeOcoOrderLocked(Long positionId, PositionOcoOrderRequest request) {
        if (request.tp() == null || request.tp().triggerPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCOのTP価格を指定してください。");
        }
        if (request.sl() == null || request.sl().triggerPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCOのSL価格を指定してください。");
        }
        ExitOrderContext context = loadExitOrderContext(positionId);
        if (triggerOrderRepository.existsByTargetPositionIdAndExitTypeAndStatusIn(positionId, ExitOrderType.TP, pendingStatuses())
                || triggerOrderRepository.existsByTargetPositionIdAndExitTypeAndStatusIn(positionId, ExitOrderType.SL, pendingStatuses())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "この建玉には未発動のTPまたはSLがすでに設定されています。");
        }

        BigDecimal tpPrice = request.tp().triggerPrice().setScale(context.currencyPair().getPriceScale(), RoundingMode.HALF_UP);
        BigDecimal slPrice = request.sl().triggerPrice().setScale(context.currencyPair().getPriceScale(), RoundingMode.HALF_UP);
        if (tpPrice.compareTo(BigDecimal.ZERO) <= 0 || slPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCOのTP/SL価格は0より大きい値を指定してください。");
        }
        validateExitDirection(context.position(), ExitOrderType.TP, tpPrice, context.marketRate());
        validateExitDirection(context.position(), ExitOrderType.SL, slPrice, context.marketRate());

        String groupId = UUID.randomUUID().toString();
        List<TriggerOrder> saved = triggerOrderRepository.saveAll(List.of(
                createExitOrder(context, ExitOrderType.TP, tpPrice, groupId),
                createExitOrder(context, ExitOrderType.SL, slPrice, groupId)
        ));
        return new PositionOcoOrderResponse(groupId, saved.stream().map(this::toExitOrderResponse).toList());
    }

    private PositionOcoOrderResponse cancelOcoOrderLocked(Long positionId, String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCOグループIDを指定してください。");
        }
        List<TriggerOrder> groupOrders = triggerOrderRepository.findByOcoGroupIdOrderByCreatedAtAsc(groupId);
        if (groupOrders.isEmpty() || groupOrders.stream().anyMatch(order -> !positionId.equals(order.getTargetPositionId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OCO注文が見つかりません: " + groupId);
        }
        List<TriggerOrder> cancelableOrders = groupOrders.stream()
                .filter(order -> order.getStatus() == TriggerOrderStatus.PENDING || order.getStatus() == TriggerOrderStatus.WAITING)
                .toList();
        if (cancelableOrders.size() != groupOrders.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "未発動のOCO注文のみグループ取消できます。");
        }
        for (TriggerOrder order : cancelableOrders) {
            order.setStatus(TriggerOrderStatus.CANCELLED);
            order.setRejectionReason("OCOグループ取消によりキャンセルしました。");
        }
        List<TriggerOrder> saved = triggerOrderRepository.saveAll(cancelableOrders);
        return new PositionOcoOrderResponse(groupId, saved.stream().map(this::toExitOrderResponse).toList());
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
        return orders.stream()
                .filter(order -> isEntryOrder(order) || order.getParentOrderId() != null)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingOrderIds() {
        return triggerOrderRepository.findByStatusInOrderByCreatedAtAsc(List.of(
                        TriggerOrderStatus.PENDING,
                        TriggerOrderStatus.WAITING
                )).stream()
                .filter(order -> isEntryOrder(order) || order.getTargetPositionId() != null)
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
        if (order.getPurpose() == TriggerOrderPurpose.EXIT) {
            evaluateExitOrderLocked(order);
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
            bindIfdChildren(order, result.trade().positionId());
        } catch (ResponseStatusException exception) {
            reject(order, exception.getReason() == null ? "Trigger execution rejected" : exception.getReason());
        }
    }

    private void evaluateExitOrderLocked(TriggerOrder order) {
        Position position = order.getTargetPositionId() == null
                ? null
                : positionRepository.findById(order.getTargetPositionId()).orElse(null);
        if (position == null || position.getStatus() != PositionStatus.OPEN) {
            expire(order, "対象建玉が決済済みのため、決済注文を失効しました。");
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
        if (marketRate == null || !isExitTriggered(order, position, marketRate)) {
            return;
        }

        try {
            BigDecimal closePrice = order.getExitType() == ExitOrderType.TP
                    ? order.getTriggerPrice()
                    : exitReferencePrice(position, marketRate);
            var result = positionService.closePositionForLockedAccount(
                    position.getId(),
                    OrderSource.TRIGGER,
                    closePrice
            );
            order.setStatus(TriggerOrderStatus.TRIGGERED);
            order.setTriggeredAt(LocalDateTime.now());
            order.setResultingOrderId(result.execution().order().id());
            triggerOrderRepository.save(order);
            cancelOcoSiblings(order);
        } catch (ResponseStatusException exception) {
            reject(order, exception.getReason() == null ? "Exit order execution rejected" : exception.getReason());
        }
    }

    private void cancelOcoSiblings(TriggerOrder triggeredOrder) {
        String groupId = triggeredOrder.getOcoGroupId();
        if (groupId == null || groupId.isBlank()) {
            return;
        }
        List<TriggerOrder> siblings = triggerOrderRepository.findByOcoGroupIdOrderByCreatedAtAsc(groupId).stream()
                .filter(order -> !Objects.equals(triggeredOrder.getId(), order.getId()))
                .filter(order -> order.getStatus() == TriggerOrderStatus.PENDING
                        || order.getStatus() == TriggerOrderStatus.WAITING
                        || order.getStatus() == TriggerOrderStatus.EXPIRED)
                .toList();
        for (TriggerOrder sibling : siblings) {
            sibling.setStatus(TriggerOrderStatus.CANCELLED);
            sibling.setRejectionReason("OCOの相手注文が約定したためキャンセルしました。");
        }
        triggerOrderRepository.saveAll(siblings);
    }

    private void reject(TriggerOrder order, String reason) {
        order.setStatus(TriggerOrderStatus.REJECTED);
        order.setTriggeredAt(LocalDateTime.now());
        order.setRejectionReason(reason);
        triggerOrderRepository.save(order);
    }

    private void expire(TriggerOrder order, String reason) {
        order.setStatus(TriggerOrderStatus.EXPIRED);
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

    private boolean isExitTriggered(TriggerOrder order, Position position, MarketRate marketRate) {
        BigDecimal price = exitReferencePrice(position, marketRate);
        if (price == null || order.getExitType() == null) {
            return false;
        }
        if (position.getSide() == PositionSide.LONG) {
            return order.getExitType() == ExitOrderType.TP
                    ? price.compareTo(order.getTriggerPrice()) >= 0
                    : price.compareTo(order.getTriggerPrice()) <= 0;
        }
        return order.getExitType() == ExitOrderType.TP
                ? price.compareTo(order.getTriggerPrice()) <= 0
                : price.compareTo(order.getTriggerPrice()) >= 0;
    }

    private void validateExitDirection(
            Position position,
            ExitOrderType type,
            BigDecimal triggerPrice,
            MarketRate marketRate
    ) {
        BigDecimal referencePrice = exitReferencePrice(position, marketRate);
        boolean valid;
        if (position.getSide() == PositionSide.LONG) {
            valid = type == ExitOrderType.TP
                    ? triggerPrice.compareTo(referencePrice) > 0
                    : triggerPrice.compareTo(referencePrice) < 0;
        } else {
            valid = type == ExitOrderType.TP
                    ? triggerPrice.compareTo(referencePrice) < 0
                    : triggerPrice.compareTo(referencePrice) > 0;
        }
        if (!valid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    buildInvalidExitPriceMessage(position, type, referencePrice)
            );
        }
    }

    private String buildInvalidExitPriceMessage(Position position, ExitOrderType type, BigDecimal referencePrice) {
        String sideLabel = position.getSide() == PositionSide.LONG ? "LONG建玉" : "SHORT建玉";
        String referenceLabel = position.getSide() == PositionSide.LONG ? "Bid" : "Ask";
        String direction;
        if (position.getSide() == PositionSide.LONG) {
            direction = type == ExitOrderType.TP ? "現在Bidより高い価格" : "現在Bidより低い価格";
        } else {
            direction = type == ExitOrderType.TP ? "現在Askより低い価格" : "現在Askより高い価格";
        }
        return sideLabel + "の" + type.name()
                + "価格の向きが不正です。現在の" + referenceLabel + "（" + referencePrice
                + "）に対して、" + direction + "を指定してください。";
    }

    private BigDecimal exitReferencePrice(Position position, MarketRate marketRate) {
        return position.getSide() == PositionSide.LONG ? marketRate.getBid() : marketRate.getAsk();
    }

    private OrderSide closeSide(PositionSide side) {
        return side == PositionSide.LONG ? OrderSide.SELL : OrderSide.BUY;
    }

    private ExitOrderContext loadExitOrderContext(Long positionId) {
        Account account = accountRepository.findByAccountNumber(DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Default demo account is not ready"));
        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "対象建玉が見つかりません: " + positionId));
        if (!account.getId().equals(position.getAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "対象建玉が見つかりません: " + positionId);
        }
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OPEN状態の建玉にのみ決済注文を設定できます。");
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
                        "Latest market rate is not available: " + currencyPair.getSymbol()
                ));
        return new ExitOrderContext(account, position, currencyPair, marketRate);
    }

    private TriggerOrder createExitOrder(
            ExitOrderContext context,
            ExitOrderType exitType,
            BigDecimal triggerPrice,
            String ocoGroupId
    ) {
        TriggerOrder order = new TriggerOrder();
        order.setAccountId(context.account().getId());
        order.setCurrencyPair(context.currencyPair().getSymbol());
        order.setSide(closeSide(context.position().getSide()));
        order.setOrderType(exitType == ExitOrderType.TP ? OrderType.LIMIT : OrderType.STOP);
        order.setQuantity(context.position().getQuantity());
        order.setTriggerPrice(triggerPrice);
        order.setStatus(TriggerOrderStatus.PENDING);
        order.setPurpose(TriggerOrderPurpose.EXIT);
        order.setExitType(exitType);
        order.setTargetPositionId(context.position().getId());
        order.setOcoGroupId(ocoGroupId);
        return order;
    }

    private void bindIfdChildren(TriggerOrder parentOrder, Long positionId) {
        if (!isEntryOrder(parentOrder) || positionId == null) {
            return;
        }
        List<TriggerOrder> children = triggerOrderRepository.findByParentOrderIdAndStatusInOrderByCreatedAtAsc(
                parentOrder.getId(),
                pendingStatuses()
        );
        if (children.isEmpty()) {
            return;
        }
        Position position = positionRepository.findById(positionId).orElse(null);
        if (position == null || position.getStatus() != PositionStatus.OPEN) {
            for (TriggerOrder child : children) {
                child.setStatus(TriggerOrderStatus.EXPIRED);
                child.setRejectionReason("IFD親注文の約定後に対象建玉を確認できないため失効しました。");
            }
            triggerOrderRepository.saveAll(children);
            return;
        }
        CurrencyPair currencyPair = currencyPairRepository.findBySymbol(position.getCurrencyPair())
                .filter(pair -> Boolean.TRUE.equals(pair.getEnabled()))
                .orElse(null);
        MarketRate marketRate = currencyPair == null
                ? null
                : marketRateRepository.findByCurrencyPair(currencyPair).orElse(null);
        for (TriggerOrder child : children) {
            if (currencyPair == null || marketRate == null || child.getExitType() == null) {
                child.setStatus(TriggerOrderStatus.EXPIRED);
                child.setRejectionReason("IFD子注文を建玉へバインドできないため失効しました。");
                continue;
            }
            try {
                validateExitDirection(position, child.getExitType(), child.getTriggerPrice(), marketRate);
                child.setTargetPositionId(position.getId());
                child.setCurrencyPair(currencyPair.getSymbol());
                child.setSide(closeSide(position.getSide()));
                child.setOrderType(child.getExitType() == ExitOrderType.TP ? OrderType.LIMIT : OrderType.STOP);
                child.setQuantity(position.getQuantity());
                child.setStatus(TriggerOrderStatus.PENDING);
                child.setRejectionReason(null);
            } catch (ResponseStatusException exception) {
                child.setStatus(TriggerOrderStatus.EXPIRED);
                child.setRejectionReason("IFD親注文の約定時点で決済注文の価格方向が不正なため失効しました。");
            }
        }
        triggerOrderRepository.saveAll(children);
    }

    private void cancelIfdChildren(TriggerOrder parentOrder) {
        if (!isEntryOrder(parentOrder)) {
            return;
        }
        List<TriggerOrder> children = triggerOrderRepository.findByParentOrderIdAndStatusInOrderByCreatedAtAsc(
                parentOrder.getId(),
                pendingStatuses()
        );
        for (TriggerOrder child : children) {
            child.setStatus(TriggerOrderStatus.CANCELLED);
            child.setRejectionReason("IFD親注文の取消によりキャンセルしました。");
        }
        triggerOrderRepository.saveAll(children);
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

    private boolean isEntryOrder(TriggerOrder order) {
        return order.getPurpose() == null || order.getPurpose() == TriggerOrderPurpose.ENTRY;
    }

    private List<TriggerOrderStatus> pendingStatuses() {
        return List.of(TriggerOrderStatus.PENDING, TriggerOrderStatus.WAITING);
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
                order.getRejectionReason(),
                order.getPurpose() == null ? TriggerOrderPurpose.ENTRY.name() : order.getPurpose().name(),
                order.getExitType() == null ? null : order.getExitType().name(),
                order.getTargetPositionId(),
                order.getParentOrderId(),
                order.getOcoGroupId()
        );
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
                order.getTriggeredAt()
        );
    }

    private record ExitOrderContext(
            Account account,
            Position position,
            CurrencyPair currencyPair,
            MarketRate marketRate
    ) {
    }
}
