package com.example.fx.demo.backend.market;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MarketRateTickRepository extends JpaRepository<MarketRateTick, Long> {

    List<MarketRateTick> findTop300ByCurrencyPair_SymbolOrderByQuotedAtDesc(String symbol);

    List<MarketRateTick> findByCurrencyPair_SymbolAndQuotedAtAfterOrderByQuotedAtAsc(String symbol, Instant quotedAt);

    void deleteByCurrencyPairAndQuotedAtBefore(CurrencyPair currencyPair, Instant quotedAt);
}
