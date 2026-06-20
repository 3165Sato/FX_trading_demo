package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.common.enums.AlertSeverity;
import com.example.fx.demo.backend.common.enums.AlertType;
import com.example.fx.demo.backend.market.dto.AlertResponse;
import com.example.fx.demo.backend.market.dto.SpreadStatsResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnomalyAlertService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final MarketRateService marketRateService;
    private final MarketRateTickRepository marketRateTickRepository;
    private final SpreadStatsService spreadStatsService;
    private final AnomalyAlertProperties properties;
    private final Map<AlertKey, AlertState> activeAlerts = new ConcurrentHashMap<>();
    private final Deque<AlertState> history = new ArrayDeque<>();

    public AnomalyAlertService(
            MarketRateService marketRateService,
            MarketRateTickRepository marketRateTickRepository,
            SpreadStatsService spreadStatsService,
            AnomalyAlertProperties properties
    ) {
        this.marketRateService = marketRateService;
        this.marketRateTickRepository = marketRateTickRepository;
        this.spreadStatsService = spreadStatsService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "#{@anomalyAlertProperties.evaluationIntervalMillis()}")
    public void evaluateAll() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        for (MarketRate rate : marketRateService.getEnabledLatestRateEntities()) {
            evaluateSpreadWide(rate, now);
            evaluateRapidMove(rate, now);
            evaluateStaleData(rate, now);
            evaluateCrossedQuote(rate, now);
        }
    }

    public List<AlertResponse> listAlerts(
            boolean activeOnly,
            String currencyPair,
            String severity,
            Integer limit
    ) {
        int normalizedLimit = normalizeLimit(limit);
        return snapshotHistory().stream()
                .filter(alert -> !activeOnly || alert.active)
                .filter(alert -> currencyPair == null || currencyPair.isBlank() || alert.key.currencyPair().equals(currencyPair))
                .filter(alert -> severity == null || severity.isBlank() || alert.severity.name().equals(severity))
                .sorted(Comparator.comparing(AlertState::raisedAt).reversed())
                .limit(normalizedLimit)
                .map(this::toResponse)
                .toList();
    }

    private void evaluateSpreadWide(MarketRate rate, Instant now) {
        if (!properties.getRules().getSpread().isEnabled()) {
            resolve(rate, AlertType.SPREAD_WIDE, now);
            return;
        }

        String symbol = rate.getCurrencyPair().getSymbol();
        SpreadStatsResponse stats = spreadStatsService.getSpreadStats(symbol, properties.getRules().getSpread().getLimit());
        AlertSeverity severity = switch (stats.status()) {
            case "VERY_WIDE" -> AlertSeverity.CRITICAL;
            case "WIDE" -> AlertSeverity.WARNING;
            default -> null;
        };

        if (severity == null) {
            resolve(rate, AlertType.SPREAD_WIDE, now);
            return;
        }

        raiseOrUpdate(
                rate,
                AlertType.SPREAD_WIDE,
                severity,
                "Spread is wider than recent baseline: " + stats.spreadPips() + " pips",
                null,
                now
        );
    }

    private void evaluateRapidMove(MarketRate rate, Instant now) {
        AnomalyAlertProperties.RapidMoveRule rule = properties.getRules().getRapidMove();
        if (!rule.isEnabled()) {
            resolve(rate, AlertType.RAPID_MOVE, now);
            return;
        }

        String symbol = rate.getCurrencyPair().getSymbol();
        Instant since = now.minusSeconds(rule.getLookbackSeconds());
        List<MarketRateTick> ticks = marketRateTickRepository
                .findByCurrencyPair_SymbolAndQuotedAtAfterOrderByQuotedAtAsc(symbol, since);
        if (ticks.isEmpty()) {
            resolve(rate, AlertType.RAPID_MOVE, now);
            return;
        }

        BigDecimal midPast = ticks.getFirst().getMidPrice();
        BigDecimal midNow = rate.getMidPrice();
        if (midPast == null || midPast.compareTo(BigDecimal.ZERO) <= 0 || midNow == null) {
            resolve(rate, AlertType.RAPID_MOVE, now);
            return;
        }

        BigDecimal delta = midNow.subtract(midPast);
        BigDecimal changeBps = delta.abs()
                .divide(midPast, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("10000"));
        AlertSeverity severity = severityForThreshold(changeBps, rule.getWarnBps(), rule.getCriticalBps());
        if (severity == null) {
            resolve(rate, AlertType.RAPID_MOVE, now);
            return;
        }

        BigDecimal changePips = toPips(delta.abs(), rate.getCurrencyPair().getPipScale());
        String direction = delta.compareTo(BigDecimal.ZERO) >= 0 ? "UP" : "DOWN";
        raiseOrUpdate(
                rate,
                AlertType.RAPID_MOVE,
                severity,
                "Rapid " + direction + " move detected: " + changeBps.setScale(1, RoundingMode.HALF_UP) + " bps",
                changePips,
                now
        );
    }

    private void evaluateStaleData(MarketRate rate, Instant now) {
        AnomalyAlertProperties.StaleDataRule rule = properties.getRules().getStaleData();
        if (!rule.isEnabled() || rate.getQuotedAt() == null) {
            resolve(rate, AlertType.STALE_DATA, now);
            return;
        }

        long ageSeconds = Duration.between(rate.getQuotedAt(), now).toSeconds();
        AlertSeverity severity = null;
        if (ageSeconds >= rule.getCriticalSeconds()) {
            severity = AlertSeverity.CRITICAL;
        } else if (ageSeconds >= rule.getWarnSeconds()) {
            severity = AlertSeverity.WARNING;
        }

        if (severity == null) {
            resolve(rate, AlertType.STALE_DATA, now);
            return;
        }

        raiseOrUpdate(
                rate,
                AlertType.STALE_DATA,
                severity,
                "Rate data is stale: " + ageSeconds + " seconds since last quote",
                null,
                now
        );
    }

    private void evaluateCrossedQuote(MarketRate rate, Instant now) {
        if (!properties.getRules().getCrossedQuote().isEnabled()) {
            resolve(rate, AlertType.CROSSED_QUOTE, now);
            return;
        }

        if (rate.getBid() != null && rate.getAsk() != null && rate.getBid().compareTo(rate.getAsk()) >= 0) {
            raiseOrUpdate(
                    rate,
                    AlertType.CROSSED_QUOTE,
                    AlertSeverity.CRITICAL,
                    "Crossed quote detected: bid is greater than or equal to ask",
                    null,
                    now
            );
            return;
        }

        resolve(rate, AlertType.CROSSED_QUOTE, now);
    }

    private AlertSeverity severityForThreshold(BigDecimal value, BigDecimal warn, BigDecimal critical) {
        if (value.compareTo(critical) >= 0) {
            return AlertSeverity.CRITICAL;
        }
        if (value.compareTo(warn) >= 0) {
            return AlertSeverity.WARNING;
        }
        return null;
    }

    private void raiseOrUpdate(
            MarketRate rate,
            AlertType type,
            AlertSeverity severity,
            String message,
            BigDecimal changePips,
            Instant now
    ) {
        AlertKey key = new AlertKey(rate.getCurrencyPair().getSymbol(), type);
        activeAlerts.compute(key, (alertKey, current) -> {
            if (current == null || !current.active) {
                AlertState next = new AlertState(
                        UUID.randomUUID().toString(),
                        key,
                        severity,
                        message,
                        changePips,
                        now,
                        null,
                        true
                );
                addHistory(next);
                return next;
            }

            current.severity = stronger(current.severity, severity);
            current.message = message;
            current.changePips = changePips;
            return current;
        });
    }

    private void resolve(MarketRate rate, AlertType type, Instant now) {
        AlertKey key = new AlertKey(rate.getCurrencyPair().getSymbol(), type);
        activeAlerts.computeIfPresent(key, (alertKey, current) -> {
            current.active = false;
            current.resolvedAt = now;
            return null;
        });
    }

    private AlertSeverity stronger(AlertSeverity current, AlertSeverity next) {
        return next.ordinal() > current.ordinal() ? next : current;
    }

    private BigDecimal toPips(BigDecimal priceDelta, Integer pipScale) {
        if (pipScale == null || pipScale <= 0) {
            return null;
        }
        return priceDelta.movePointRight(pipScale).setScale(1, RoundingMode.HALF_UP);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private void addHistory(AlertState alert) {
        synchronized (history) {
            history.addFirst(alert);
            while (history.size() > properties.getHistory().getMaxEntries()) {
                history.removeLast();
            }
        }
    }

    private List<AlertState> snapshotHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    private AlertResponse toResponse(AlertState alert) {
        return new AlertResponse(
                alert.id,
                alert.key.type().name(),
                alert.key.currencyPair(),
                alert.severity.name(),
                alert.message,
                alert.changePips,
                alert.raisedAt,
                alert.resolvedAt,
                alert.active
        );
    }

    private record AlertKey(String currencyPair, AlertType type) {
    }

    private static class AlertState {
        private final String id;
        private final AlertKey key;
        private AlertSeverity severity;
        private String message;
        private BigDecimal changePips;
        private final Instant raisedAt;
        private Instant resolvedAt;
        private boolean active;

        private AlertState(
                String id,
                AlertKey key,
                AlertSeverity severity,
                String message,
                BigDecimal changePips,
                Instant raisedAt,
                Instant resolvedAt,
                boolean active
        ) {
            this.id = id;
            this.key = key;
            this.severity = severity;
            this.message = message;
            this.changePips = changePips;
            this.raisedAt = raisedAt;
            this.resolvedAt = resolvedAt;
            this.active = active;
        }

        private Instant raisedAt() {
            return raisedAt;
        }
    }
}
