package com.example.fx.demo.backend.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FxOrderRepository extends JpaRepository<FxOrder, Long> {
}
