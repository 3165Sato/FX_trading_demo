package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.order.TriggerOrderService;
import com.example.fx.demo.backend.position.dto.PnlSummaryResponse;
import com.example.fx.demo.backend.position.dto.PositionCloseResponse;
import com.example.fx.demo.backend.position.dto.PositionExitOrderRequest;
import com.example.fx.demo.backend.position.dto.PositionExitOrderResponse;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trade")
public class PositionController {

    private final PositionService positionService;
    private final TriggerOrderService triggerOrderService;

    public PositionController(PositionService positionService, TriggerOrderService triggerOrderService) {
        this.positionService = positionService;
        this.triggerOrderService = triggerOrderService;
    }

    @GetMapping("/positions")
    public List<PositionResponse> getPositions(
            @RequestParam(required = false) String currencyPair
    ) {
        return positionService.getPositions(currencyPair);
    }

    @GetMapping("/pnl/summary")
    public PnlSummaryResponse getPnlSummary() {
        return positionService.getPnlSummary();
    }

    @PostMapping("/positions/{id}/close")
    public PositionCloseResponse closePosition(@PathVariable Long id) {
        return positionService.closePosition(id);
    }

    @PostMapping("/positions/{id}/exit-orders")
    public PositionExitOrderResponse placeExitOrder(
            @PathVariable Long id,
            @RequestBody PositionExitOrderRequest request
    ) {
        return triggerOrderService.placeExitOrder(id, request);
    }

    @DeleteMapping("/positions/{id}/exit-orders/{exitId}")
    public PositionExitOrderResponse cancelExitOrder(
            @PathVariable Long id,
            @PathVariable Long exitId
    ) {
        return triggerOrderService.cancelExitOrder(id, exitId);
    }
}
