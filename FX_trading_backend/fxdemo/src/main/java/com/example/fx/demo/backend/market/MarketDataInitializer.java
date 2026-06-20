package com.example.fx.demo.backend.market;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class MarketDataInitializer implements CommandLineRunner {

    private final CurrencyPairRepository currencyPairRepository;
    private final MarketRateRepository marketRateRepository;

    public MarketDataInitializer(
            CurrencyPairRepository currencyPairRepository,
            MarketRateRepository marketRateRepository
    ) {
        this.currencyPairRepository = currencyPairRepository;
        this.marketRateRepository = marketRateRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        CurrencyPair usdJpy = findOrCreateCurrencyPair("USD/JPY", "USD", "JPY", 3, 0, 2);
        CurrencyPair eurJpy = findOrCreateCurrencyPair("EUR/JPY", "EUR", "JPY", 3, 0, 2);
        CurrencyPair eurUsd = findOrCreateCurrencyPair("EUR/USD", "EUR", "USD", 5, 0, 4);

        createMarketRateIfAbsent(
                usdJpy,
                new BigDecimal("155.120"),
                new BigDecimal("155.123"),
                new BigDecimal("155.1215"),
                new BigDecimal("0.003")
        );
        createMarketRateIfAbsent(
                eurJpy,
                new BigDecimal("168.250"),
                new BigDecimal("168.255"),
                new BigDecimal("168.2525"),
                new BigDecimal("0.005")
        );
        createMarketRateIfAbsent(
                eurUsd,
                new BigDecimal("1.08500"),
                new BigDecimal("1.08503"),
                new BigDecimal("1.085015"),
                new BigDecimal("0.00003")
        );
    }

    private CurrencyPair findOrCreateCurrencyPair(
            String symbol,
            String baseCurrency,
            String quoteCurrency,
            Integer priceScale,
            Integer quantityScale,
            Integer pipScale
    ) {
        return currencyPairRepository.findBySymbol(symbol)
                .orElseGet(() -> {
                    CurrencyPair currencyPair = new CurrencyPair();
                    currencyPair.setSymbol(symbol);
                    currencyPair.setBaseCurrency(baseCurrency);
                    currencyPair.setQuoteCurrency(quoteCurrency);
                    currencyPair.setPriceScale(priceScale);
                    currencyPair.setQuantityScale(quantityScale);
                    currencyPair.setPipScale(pipScale);
                    currencyPair.setEnabled(true);
                    return currencyPairRepository.save(currencyPair);
                });
    }

    private void createMarketRateIfAbsent(
            CurrencyPair currencyPair,
            BigDecimal bid,
            BigDecimal ask,
            BigDecimal midPrice,
            BigDecimal spread
    ) {
        // 既に最新レートがある場合は、学習用の初期値で上書きしない。
        if (marketRateRepository.findByCurrencyPair(currencyPair).isPresent()) {
            return;
        }

        MarketRate marketRate = new MarketRate();
        marketRate.setCurrencyPair(currencyPair);
        marketRate.setBid(bid);
        marketRate.setAsk(ask);
        marketRate.setMidPrice(midPrice);
        marketRate.setSpread(spread);
        marketRate.setQuotedAt(Instant.now());
        marketRateRepository.save(marketRate);
    }
}
