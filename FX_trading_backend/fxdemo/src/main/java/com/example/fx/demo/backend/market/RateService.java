package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.common.enums.CurrencyPair;
import org.springframework.stereotype.Service;

@Service
public class RateService {

    private final RateSimulator rateSimulator;

    public RateService(RateSimulator rateSimulator) {
        this.rateSimulator = rateSimulator;
    }

    public Rate getCurrentRate(CurrencyPair currencyPair) {
        return rateSimulator.currentRate(currencyPair);
    }
}
