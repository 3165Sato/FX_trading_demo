package com.example.fx.demo.backend.market;

import com.example.fx.demo.backend.common.enums.NewsDirection;
import com.example.fx.demo.backend.market.dto.NewsEventRequest;
import com.example.fx.demo.backend.market.dto.NewsEventResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class NewsEventService {

    private static final int HISTORY_SIZE = 50;
    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;

    private final CurrencyPairRepository currencyPairRepository;
    private final NewsEventProperties properties;
    private final Map<String, ActiveNewsEvent> activeEvents = new ConcurrentHashMap<>();
    private final Deque<ActiveNewsEvent> eventHistory = new ArrayDeque<>();
    private final Random random = new Random();

    public NewsEventService(CurrencyPairRepository currencyPairRepository, NewsEventProperties properties) {
        this.currencyPairRepository = currencyPairRepository;
        this.properties = properties;
    }

    public NewsEventResponse trigger(NewsEventRequest request) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "News events are disabled");
        }
        if (request.currencyPair() == null || request.currencyPair().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyPair is required");
        }

        CurrencyPair currencyPair = currencyPairRepository.findBySymbol(request.currencyPair())
                .filter(pair -> Boolean.TRUE.equals(pair.getEnabled()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Currency pair not found: " + request.currencyPair()
                ));

        Instant startedAt = Instant.now();
        int durationSeconds = normalizeDuration(request.durationSeconds());
        ActiveNewsEvent event = new ActiveNewsEvent(
                UUID.randomUUID().toString(),
                currencyPair.getSymbol(),
                request.direction() == null ? randomDirection() : request.direction(),
                normalizeMagnitude(request.magnitudeBps()),
                normalizePositive(request.volatilityMultiplier(), properties.getDefaults().getVolatilityMultiplier()),
                normalizePositive(request.spreadMultiplier(), properties.getDefaults().getSpreadMultiplier()),
                durationSeconds,
                normalizeHeadline(request.headline()),
                startedAt,
                startedAt.plusSeconds(durationSeconds)
        );

        activeEvents.put(currencyPair.getSymbol(), event);
        addHistory(event);
        return toResponse(event, startedAt);
    }

    public EventModifiers consumeTick(String symbol, Instant now) {
        ActiveNewsEvent event = activeEvents.get(symbol);
        if (event == null) {
            return EventModifiers.neutral();
        }
        if (!event.isActive(now)) {
            activeEvents.remove(symbol, event);
            return EventModifiers.neutral();
        }

        BigDecimal signedJump = BigDecimal.ZERO;
        if (event.jumpConsumed.compareAndSet(false, true)) {
            signedJump = event.direction == NewsDirection.UP
                    ? event.magnitudeBps
                    : event.magnitudeBps.negate();
        }

        return new EventModifiers(
                signedJump,
                event.volatilityMultiplier,
                event.spreadMultiplier,
                true
        );
    }

    public List<NewsEventResponse> listEvents(boolean activeOnly, Integer limit) {
        Instant now = Instant.now();
        int normalizedLimit = normalizeListLimit(limit);

        return snapshotHistory().stream()
                .filter(event -> !activeOnly || event.isActive(now))
                .sorted(Comparator.comparing(ActiveNewsEvent::startedAt).reversed())
                .limit(normalizedLimit)
                .map(event -> toResponse(event, now))
                .toList();
    }

    private BigDecimal normalizeMagnitude(BigDecimal magnitudeBps) {
        BigDecimal candidate = magnitudeBps == null ? properties.getDefaults().getMagnitudeBps() : magnitudeBps;
        if (candidate.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal max = properties.getLimits().getMaxMagnitudeBps();
        return candidate.compareTo(max) > 0 ? max : candidate;
    }

    private int normalizeDuration(Integer durationSeconds) {
        int candidate = durationSeconds == null ? properties.getDefaults().getDurationSeconds() : durationSeconds;
        if (candidate < 1) {
            return 1;
        }
        return Math.min(candidate, properties.getLimits().getMaxDurationSeconds());
    }

    private double normalizePositive(Double value, double fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private int normalizeListLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIST_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    private NewsDirection randomDirection() {
        return random.nextBoolean() ? NewsDirection.UP : NewsDirection.DOWN;
    }

    private String normalizeHeadline(String headline) {
        if (headline != null && !headline.isBlank()) {
            return headline;
        }
        List<String> headlines = properties.getHeadlines();
        if (headlines == null || headlines.isEmpty()) {
            return "Fictional market outlook changes abruptly";
        }
        return headlines.get(random.nextInt(headlines.size()));
    }

    private void addHistory(ActiveNewsEvent event) {
        synchronized (eventHistory) {
            eventHistory.addFirst(event);
            while (eventHistory.size() > HISTORY_SIZE) {
                eventHistory.removeLast();
            }
        }
    }

    private List<ActiveNewsEvent> snapshotHistory() {
        synchronized (eventHistory) {
            return new ArrayList<>(eventHistory);
        }
    }

    private NewsEventResponse toResponse(ActiveNewsEvent event, Instant now) {
        return new NewsEventResponse(
                event.id,
                event.currencyPair,
                event.direction.name(),
                event.magnitudeBps,
                event.volatilityMultiplier,
                event.spreadMultiplier,
                event.durationSeconds,
                event.headline,
                event.startedAt,
                event.endsAt,
                event.isActive(now)
        );
    }

    private record ActiveNewsEvent(
            String id,
            String currencyPair,
            NewsDirection direction,
            BigDecimal magnitudeBps,
            double volatilityMultiplier,
            double spreadMultiplier,
            int durationSeconds,
            String headline,
            Instant startedAt,
            Instant endsAt,
            AtomicBoolean jumpConsumed
    ) {

        private ActiveNewsEvent(
                String id,
                String currencyPair,
                NewsDirection direction,
                BigDecimal magnitudeBps,
                double volatilityMultiplier,
                double spreadMultiplier,
                int durationSeconds,
                String headline,
                Instant startedAt,
                Instant endsAt
        ) {
            this(
                    id,
                    currencyPair,
                    direction,
                    magnitudeBps,
                    volatilityMultiplier,
                    spreadMultiplier,
                    durationSeconds,
                    headline,
                    startedAt,
                    endsAt,
                    new AtomicBoolean(false)
            );
        }

        private boolean isActive(Instant now) {
            return !now.isBefore(startedAt) && now.isBefore(endsAt);
        }
    }
}
