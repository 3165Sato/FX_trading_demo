package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.market.dto.MarketRateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MarketRateService {

    private final MarketRateRepository marketRateRepository;

    public MarketRateService(MarketRateRepository marketRateRepository) {
        this.marketRateRepository = marketRateRepository;
    }

    public List<MarketRateResponse> getAllLatestRates() {
        return marketRateRepository.findAll().stream()
                .sorted(Comparator.comparing(rate -> rate.getCurrencyPair().getSymbol()))
                .map(this::toResponse)
                .toList();
    }

    public MarketRateResponse getLatestRate(String currencyPair) {
        return marketRateRepository.findByCurrencyPair_Symbol(currencyPair)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Latest market rate not found: " + currencyPair));
    }

    public List<MarketRate> getEnabledLatestRateEntities() {
        return marketRateRepository.findByCurrencyPair_EnabledTrue();
    }

    @Transactional
    public void updateLatestRate(
            MarketRate marketRate,
            BigDecimal bid,
            BigDecimal ask,
            BigDecimal midPrice,
            Instant quotedAt
    ) {
        marketRate.setBid(bid);
        marketRate.setAsk(ask);
        marketRate.setMidPrice(midPrice);
        marketRate.setQuotedAt(quotedAt);
        marketRateRepository.save(marketRate);
    }

    private MarketRateResponse toResponse(MarketRate marketRate) {
        // DTO returns the symbol only, not the CurrencyPair entity.
        return new MarketRateResponse(
                marketRate.getCurrencyPair().getSymbol(),
                marketRate.getBid(),
                marketRate.getAsk(),
                marketRate.getMidPrice(),
                marketRate.getSpread(),
                marketRate.getQuotedAt()
        );
    }
}
