package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.market.dto.MarketRateResponse;
import com.example.fx.demo.backend.market.dto.MarketRateTickResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/rates")
public class MarketRateController {

    private final MarketRateService marketRateService;

    public MarketRateController(MarketRateService marketRateService) {
        this.marketRateService = marketRateService;
    }

    @GetMapping
    public List<MarketRateResponse> getAllLatestRates() {
        return marketRateService.getAllLatestRates();
    }

    @GetMapping("/latest")
    public MarketRateResponse getLatestRate(@RequestParam String currencyPair) {
        return marketRateService.getLatestRate(currencyPair);
    }

    @GetMapping("/ticks")
    public List<MarketRateTickResponse> getRecentTicks(
            @RequestParam String currencyPair,
            @RequestParam(defaultValue = "300") int limit
    ) {
        return marketRateService.getRecentTicks(currencyPair, limit);
    }
}
