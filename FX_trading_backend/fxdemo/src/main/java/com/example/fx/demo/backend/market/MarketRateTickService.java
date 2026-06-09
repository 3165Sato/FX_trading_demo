package com.example.fx.demo.backend.market;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MarketRateTickService {

    private final MarketRateTickRepository marketRateTickRepository;

    public MarketRateTickService(MarketRateTickRepository marketRateTickRepository) {
        this.marketRateTickRepository = marketRateTickRepository;
    }

    @Transactional
    public void saveTick(MarketRate marketRate) {
        MarketRateTick tick = new MarketRateTick();
        tick.setCurrencyPair(marketRate.getCurrencyPair());
        tick.setBid(marketRate.getBid());
        tick.setAsk(marketRate.getAsk());
        tick.setMidPrice(marketRate.getMidPrice());
        tick.setSpread(marketRate.getSpread());
        tick.setQuotedAt(marketRate.getQuotedAt());
        marketRateTickRepository.save(tick);
    }

    @Transactional
    public void deleteTicksBefore(CurrencyPair currencyPair, Instant quotedAt) {
        marketRateTickRepository.deleteByCurrencyPairAndQuotedAtBefore(currencyPair, quotedAt);
    }
}
