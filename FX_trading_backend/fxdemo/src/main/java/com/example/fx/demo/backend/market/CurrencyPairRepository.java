package com.example.fx.demo.backend.market;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyPairRepository extends JpaRepository<CurrencyPair, Long> {
}
