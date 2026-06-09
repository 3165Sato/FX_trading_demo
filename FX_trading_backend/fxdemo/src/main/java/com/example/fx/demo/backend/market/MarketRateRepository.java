package com.example.fx.demo.backend.market;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface MarketRateRepository extends JpaRepository<MarketRate, Long> {

    Optional<MarketRate> findByCurrencyPair(CurrencyPair currencyPair);

    Optional<MarketRate> findByCurrencyPair_Symbol(String symbol);

    @EntityGraph(attributePaths = "currencyPair")
    List<MarketRate> findByCurrencyPair_EnabledTrue();
}
