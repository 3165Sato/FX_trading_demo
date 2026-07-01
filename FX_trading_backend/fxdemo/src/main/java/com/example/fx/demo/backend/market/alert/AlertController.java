package com.example.fx.demo.backend.market.alert;

import com.example.fx.demo.backend.market.dto.AlertResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market/alerts")
public class AlertController {

    private final AnomalyAlertService anomalyAlertService;

    public AlertController(AnomalyAlertService anomalyAlertService) {
        this.anomalyAlertService = anomalyAlertService;
    }

    @GetMapping
    public List<AlertResponse> listAlerts(
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(required = false) String currencyPair,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return anomalyAlertService.listAlerts(activeOnly, currencyPair, severity, limit);
    }
}
