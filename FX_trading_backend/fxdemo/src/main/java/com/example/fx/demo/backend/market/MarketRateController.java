package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.market.dto.MarketRateResponse;
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
        // 入力検証や例外整形は後続ステップで共通化する。
        return marketRateService.getLatestRate(currencyPair);
    }
}
