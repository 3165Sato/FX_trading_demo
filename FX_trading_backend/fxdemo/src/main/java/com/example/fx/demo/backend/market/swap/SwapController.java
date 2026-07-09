package com.example.fx.demo.backend.market.swap;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/swap")
public class SwapController {

    private final SwapRolloverService swapRolloverService;

    public SwapController(SwapRolloverService swapRolloverService) {
        this.swapRolloverService = swapRolloverService;
    }

    @PostMapping("/rollover")
    public SwapRolloverResponse applyRollover(@RequestBody(required = false) SwapRolloverRequest request) {
        int days = request == null || request.days() == null ? 1 : request.days();
        return swapRolloverService.applyRollover(days);
    }
}
