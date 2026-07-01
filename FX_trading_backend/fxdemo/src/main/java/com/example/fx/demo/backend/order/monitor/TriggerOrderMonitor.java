package com.example.fx.demo.backend.order.monitor;

import com.example.fx.demo.backend.order.config.PendingOrderProperties;
import com.example.fx.demo.backend.order.service.TriggerOrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TriggerOrderMonitor {

    private final PendingOrderProperties properties;
    private final TriggerOrderService triggerOrderService;

    public TriggerOrderMonitor(PendingOrderProperties properties, TriggerOrderService triggerOrderService) {
        this.properties = properties;
        this.triggerOrderService = triggerOrderService;
    }

    @Scheduled(fixedDelayString = "#{@pendingOrderProperties.evaluationIntervalMillis()}")
    public void evaluatePendingOrders() {
        if (!properties.isEnabled()) {
            return;
        }
        for (Long id : triggerOrderService.findPendingOrderIds()) {
            triggerOrderService.evaluatePendingOrder(id);
        }
    }
}
