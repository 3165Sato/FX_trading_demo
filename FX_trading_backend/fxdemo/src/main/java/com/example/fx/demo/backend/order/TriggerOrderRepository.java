package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.common.enums.ExitOrderType;
import com.example.fx.demo.backend.common.enums.TriggerOrderPurpose;
import com.example.fx.demo.backend.common.enums.TriggerOrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TriggerOrderRepository extends JpaRepository<TriggerOrder, Long> {

    List<TriggerOrder> findByStatusInOrderByCreatedAtAsc(Collection<TriggerOrderStatus> statuses);

    List<TriggerOrder> findByStatusOrderByCreatedAtDesc(TriggerOrderStatus status, Pageable pageable);

    List<TriggerOrder> findByCurrencyPairAndStatusOrderByCreatedAtDesc(
            String currencyPair,
            TriggerOrderStatus status,
            Pageable pageable
    );

    List<TriggerOrder> findByCurrencyPairOrderByCreatedAtDesc(String currencyPair, Pageable pageable);

    List<TriggerOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByTargetPositionIdAndExitTypeAndStatusIn(
            Long targetPositionId,
            ExitOrderType exitType,
            Collection<TriggerOrderStatus> statuses
    );

    List<TriggerOrder> findByTargetPositionIdAndPurposeAndStatusInOrderByCreatedAtAsc(
            Long targetPositionId,
            TriggerOrderPurpose purpose,
            Collection<TriggerOrderStatus> statuses
    );

    List<TriggerOrder> findByTargetPositionIdInAndPurposeOrderByCreatedAtAsc(
            Collection<Long> targetPositionIds,
            TriggerOrderPurpose purpose
    );

    List<TriggerOrder> findByOcoGroupIdOrderByCreatedAtAsc(String ocoGroupId);

    List<TriggerOrder> findByParentOrderIdOrderByCreatedAtAsc(Long parentOrderId);

    List<TriggerOrder> findByParentOrderIdAndStatusInOrderByCreatedAtAsc(
            Long parentOrderId,
            Collection<TriggerOrderStatus> statuses
    );
}
