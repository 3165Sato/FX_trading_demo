package com.example.fx.demo.backend.position.api;

import com.example.fx.demo.backend.position.service.PositionService;
import com.example.fx.demo.backend.order.service.TriggerOrderService;
import com.example.fx.demo.backend.position.dto.PnlSummaryResponse;
import com.example.fx.demo.backend.position.dto.PositionCloseResponse;
import com.example.fx.demo.backend.position.dto.PositionExitOrderRequest;
import com.example.fx.demo.backend.position.dto.PositionExitOrderResponse;
import com.example.fx.demo.backend.position.dto.PositionOcoOrderRequest;
import com.example.fx.demo.backend.position.dto.PositionOcoOrderResponse;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import com.example.fx.demo.backend.position.dto.PositionSwapTransferResponse;
import com.example.fx.demo.backend.position.dto.SwapTransferAllResponse;
import com.example.fx.demo.backend.position.service.SwapTransferService;
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
    private final SwapTransferService swapTransferService;
    private final TriggerOrderService triggerOrderService;

    public PositionController(
            PositionService positionService,
            SwapTransferService swapTransferService,
            TriggerOrderService triggerOrderService
    ) {
        this.positionService = positionService;
        this.swapTransferService = swapTransferService;
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

    @PostMapping("/positions/{id}/swap-transfer")
    public PositionSwapTransferResponse transferPositionSwap(@PathVariable Long id) {
        return swapTransferService.transfer(id);
    }

    @PostMapping("/swap-transfer")
    public SwapTransferAllResponse transferAllPositionSwaps() {
        return swapTransferService.transferAll();
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

    @PostMapping("/positions/{id}/oco-orders")
    public PositionOcoOrderResponse placeOcoOrder(
            @PathVariable Long id,
            @RequestBody PositionOcoOrderRequest request
    ) {
        return triggerOrderService.placeOcoOrder(id, request);
    }

    @DeleteMapping("/positions/{id}/oco-orders/{groupId}")
    public PositionOcoOrderResponse cancelOcoOrder(
            @PathVariable Long id,
            @PathVariable String groupId
    ) {
        return triggerOrderService.cancelOcoOrder(id, groupId);
    }
}
