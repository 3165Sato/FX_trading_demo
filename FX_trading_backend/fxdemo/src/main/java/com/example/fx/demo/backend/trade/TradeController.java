package com.example.fx.demo.backend.trade;

import com.example.fx.demo.backend.trade.dto.MarketOrderRequest;
import com.example.fx.demo.backend.trade.dto.OrderResultResponse;
import com.example.fx.demo.backend.trade.dto.OrderSummaryResponse;
import com.example.fx.demo.backend.trade.dto.TradeSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
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

    public TradeController(TradeExecutionService tradeExecutionService) {
        this.tradeExecutionService = tradeExecutionService;
    }

    @PostMapping("/orders/market")
    public OrderResultResponse placeMarketOrder(@RequestBody MarketOrderRequest request) {
        return tradeExecutionService.placeMarketOrder(request);
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
