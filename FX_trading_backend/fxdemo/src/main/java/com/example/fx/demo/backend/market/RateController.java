package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.common.enums.CurrencyPair;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rates")
public class RateController {

    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping("/{currencyPair}")
    public Rate currentRate(@PathVariable CurrencyPair currencyPair) {
        return rateService.getCurrentRate(currencyPair);
    }
}
