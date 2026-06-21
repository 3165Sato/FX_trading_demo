package com.example.fx.demo.backend.position;

import com.example.fx.demo.backend.position.dto.PnlSummaryResponse;
import com.example.fx.demo.backend.position.dto.PositionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trade")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
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
}
