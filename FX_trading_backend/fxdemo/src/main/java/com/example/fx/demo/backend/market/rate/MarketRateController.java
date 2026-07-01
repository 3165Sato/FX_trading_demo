package com.example.fx.demo.backend.market.rate;

import com.example.fx.demo.backend.market.spread.SpreadStatsService;
import com.example.fx.demo.backend.market.dto.MarketRateResponse;
import com.example.fx.demo.backend.market.dto.MarketRateTickResponse;
import com.example.fx.demo.backend.market.dto.SpreadStatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketRateController {

    private final MarketRateService marketRateService;
    private final SpreadStatsService spreadStatsService;

    public MarketRateController(
            MarketRateService marketRateService,
            SpreadStatsService spreadStatsService
    ) {
        this.marketRateService = marketRateService;
        this.spreadStatsService = spreadStatsService;
    }

    @GetMapping("/rates")
    public List<MarketRateResponse> getAllLatestRates() {
        return marketRateService.getAllLatestRates();
    }

    @GetMapping("/rates/latest")
    public MarketRateResponse getLatestRate(@RequestParam String currencyPair) {
        return marketRateService.getLatestRate(currencyPair);
    }

    @GetMapping("/rates/ticks")
    public List<MarketRateTickResponse> getRecentTicks(
            @RequestParam String currencyPair,
            @RequestParam(defaultValue = "300") int limit
    ) {
        return marketRateService.getRecentTicks(currencyPair, limit);
    }

    @GetMapping("/spread/stats")
    public SpreadStatsResponse getSpreadStats(
            @RequestParam String currencyPair,
            @RequestParam(defaultValue = "60") int limit
    ) {
        return spreadStatsService.getSpreadStats(currencyPair, limit);
    }
}
