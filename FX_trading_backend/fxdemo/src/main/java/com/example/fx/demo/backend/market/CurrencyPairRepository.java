package com.example.fx.demo.backend.market;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CurrencyPairRepository extends JpaRepository<CurrencyPair, Long> {

    Optional<CurrencyPair> findBySymbol(String symbol);
}
