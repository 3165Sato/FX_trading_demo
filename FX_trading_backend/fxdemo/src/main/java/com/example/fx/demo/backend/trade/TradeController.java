package com.example.fx.demo.backend.trade;

import com.example.fx.demo.backend.order.TriggerOrderService;
import com.example.fx.demo.backend.order.dto.IfdOrderRequest;
import com.example.fx.demo.backend.order.dto.IfdOrderResponse;
import com.example.fx.demo.backend.order.dto.IfoOrderRequest;
import com.example.fx.demo.backend.order.dto.IfoOrderResponse;
import com.example.fx.demo.backend.order.dto.PendingOrderRequest;
import com.example.fx.demo.backend.order.dto.PendingOrderResponse;
import com.example.fx.demo.backend.trade.dto.MarketOrderRequest;
import com.example.fx.demo.backend.trade.dto.OrderResultResponse;
import com.example.fx.demo.backend.trade.dto.OrderSummaryResponse;
import com.example.fx.demo.backend.trade.dto.TradeSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trade")
public class TradeController {

    private final TradeExecutionService tradeExecutionService;
    private final TriggerOrderService triggerOrderService;

    public TradeController(TradeExecutionService tradeExecutionService, TriggerOrderService triggerOrderService) {
        this.tradeExecutionService = tradeExecutionService;
        this.triggerOrderService = triggerOrderService;
    }

    @PostMapping("/orders/market")
    public OrderResultResponse placeMarketOrder(@RequestBody MarketOrderRequest request) {
        return tradeExecutionService.placeMarketOrder(request);
    }

    @PostMapping("/orders/pending")
    public PendingOrderResponse placePendingOrder(@RequestBody PendingOrderRequest request) {
        return triggerOrderService.placePendingOrder(request);
    }

    @PostMapping("/orders/ifd")
    public IfdOrderResponse placeIfdOrder(@RequestBody IfdOrderRequest request) {
        return triggerOrderService.placeIfdOrder(request);
    }

    @PostMapping("/orders/ifo")
    public IfoOrderResponse placeIfoOrder(@RequestBody IfoOrderRequest request) {
        return triggerOrderService.placeIfoOrder(request);
    }

    @PostMapping("/orders/pending/{id}/cancel")
    public PendingOrderResponse cancelPendingOrder(@PathVariable Long id) {
        return triggerOrderService.cancelPendingOrder(id);
    }

    @GetMapping("/orders/pending")
    public List<PendingOrderResponse> getPendingOrders(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(required = false) String currencyPair,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return triggerOrderService.listPendingOrders(status, currencyPair, limit);
    }

    @GetMapping("/trades")
    public List<TradeSummaryResponse> getTrades(
            @RequestParam(required = false) String currencyPair,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return tradeExecutionService.getTrades(currencyPair, limit);
    }

    @GetMapping("/orders")
    public List<OrderSummaryResponse> getOrders(
            @RequestParam(required = false) String currencyPair,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return tradeExecutionService.getOrders(currencyPair, limit);
    }
}
