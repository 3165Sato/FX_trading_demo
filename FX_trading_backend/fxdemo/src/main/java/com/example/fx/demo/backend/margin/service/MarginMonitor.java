package com.example.fx.demo.backend.margin.service;

import com.example.fx.demo.backend.account.service.AccountSummaryService;
import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.common.enums.AlertSeverity;
import com.example.fx.demo.backend.common.enums.AlertType;
import com.example.fx.demo.backend.market.alert.AnomalyAlertService;
import com.example.fx.demo.backend.margin.config.MarginProperties;
import com.example.fx.demo.backend.trade.service.TradeExecutionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class MarginMonitor {

    private final AccountSummaryService accountSummaryService;
    private final AnomalyAlertService anomalyAlertService;
    private final MarginProperties marginProperties;
    private final TradeExecutionService tradeExecutionService;

    public MarginMonitor(
            AccountSummaryService accountSummaryService,
            AnomalyAlertService anomalyAlertService,
            MarginProperties marginProperties,
            TradeExecutionService tradeExecutionService
    ) {
        this.accountSummaryService = accountSummaryService;
        this.anomalyAlertService = anomalyAlertService;
        this.marginProperties = marginProperties;
        this.tradeExecutionService = tradeExecutionService;
    }

    @Scheduled(fixedDelayString = "#{@marginProperties.lossCut.evaluationIntervalMillis()}")
    public void evaluateDefaultAccount() {
        Instant now = Instant.now();
        if (!marginProperties.getLossCut().isEnabled()) {
            anomalyAlertService.resolveAccountAlert(AlertType.MARGIN_WARNING, now);
            anomalyAlertService.resolveAccountAlert(AlertType.LOSS_CUT, now);
            return;
        }

        AccountSummaryResponse summary = accountSummaryService.getDefaultAccountSummary();
        BigDecimal ratio = summary.marginRatio();
        if (ratio == null) {
            anomalyAlertService.resolveAccountAlert(AlertType.MARGIN_WARNING, now);
            anomalyAlertService.resolveAccountAlert(AlertType.LOSS_CUT, now);
            return;
        }

        BigDecimal lossCutThreshold = marginProperties.getLossCut().getThresholdPercent();
        BigDecimal warningThreshold = marginProperties.getLossCut().getWarningPercent();
        if (ratio.compareTo(lossCutThreshold) <= 0) {
            var liquidated = tradeExecutionService.liquidateAllPositionsIfMarginRatioAtOrBelow(lossCutThreshold);
            if (!liquidated.isEmpty()) {
                anomalyAlertService.raiseAccountAlert(
                        AlertType.LOSS_CUT,
                        AlertSeverity.CRITICAL,
                        "Loss cut executed: margin ratio " + ratio + "%, closed positions " + liquidated.size(),
                        now
                );
            }
            anomalyAlertService.resolveAccountAlert(AlertType.MARGIN_WARNING, now);
            return;
        }

        anomalyAlertService.resolveAccountAlert(AlertType.LOSS_CUT, now);
        if (ratio.compareTo(warningThreshold) <= 0) {
            anomalyAlertService.raiseAccountAlert(
                    AlertType.MARGIN_WARNING,
                    AlertSeverity.WARNING,
                    "Margin ratio is in warning zone: " + ratio + "%",
                    now
            );
            return;
        }

        anomalyAlertService.resolveAccountAlert(AlertType.MARGIN_WARNING, now);
    }
}
