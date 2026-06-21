package com.example.fx.demo.backend.margin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarginRuleRepository extends JpaRepository<MarginRule, Long> {

    Optional<MarginRule> findByCurrencyPairAndEnabledTrue(String currencyPair);
}
