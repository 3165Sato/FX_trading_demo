package com.example.fx.demo.backend.position;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.fx.demo.backend.common.enums.PositionStatus;

import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {
    List<Position> findByAccountId(Long accountId);

    List<Position> findByAccountIdAndStatusOrderByOpenedAtAsc(Long accountId, PositionStatus status);

    List<Position> findByAccountIdAndCurrencyPairAndStatusOrderByOpenedAtAsc(
            Long accountId,
            String currencyPair,
            PositionStatus status
    );
}
