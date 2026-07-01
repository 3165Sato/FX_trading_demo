package com.example.fx.demo.backend.trade.repository;

import com.example.fx.demo.backend.trade.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByAccountId(Long accountId);

    List<Trade> findByAccountIdOrderByExecutedAtAsc(Long accountId);

    List<Trade> findByAccountIdAndCurrencyPairOrderByExecutedAtAsc(Long accountId, String currencyPair);

    List<Trade> findByCurrencyPairOrderByExecutedAtDesc(String currencyPair, Pageable pageable);

    List<Trade> findAllByOrderByExecutedAtDesc(Pageable pageable);
}
