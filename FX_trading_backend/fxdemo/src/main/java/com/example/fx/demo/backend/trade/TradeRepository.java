package com.example.fx.demo.backend.trade;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByAccountId(Long accountId);

    List<Trade> findByCurrencyPairOrderByExecutedAtDesc(String currencyPair, Pageable pageable);

    List<Trade> findAllByOrderByExecutedAtDesc(Pageable pageable);
}
