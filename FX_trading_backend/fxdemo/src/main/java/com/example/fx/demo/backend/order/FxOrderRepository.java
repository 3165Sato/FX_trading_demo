package com.example.fx.demo.backend.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FxOrderRepository extends JpaRepository<FxOrder, Long> {

    List<FxOrder> findByCurrencyPairOrderByRequestedAtDesc(String currencyPair, Pageable pageable);

    List<FxOrder> findAllByOrderByRequestedAtDesc(Pageable pageable);
}
