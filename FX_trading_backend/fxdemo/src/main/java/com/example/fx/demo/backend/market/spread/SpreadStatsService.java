package com.example.fx.demo.backend.market.spread;

import com.example.fx.demo.backend.market.pair.CurrencyPair;
import com.example.fx.demo.backend.market.rate.MarketRate;
import com.example.fx.demo.backend.market.pair.CurrencyPairRepository;
import com.example.fx.demo.backend.market.rate.MarketRateRepository;
import com.example.fx.demo.backend.market.rate.MarketRateTick;
import com.example.fx.demo.backend.market.rate.MarketRateTickRepository;
import com.example.fx.demo.backend.common.enums.SpreadStatus;
import com.example.fx.demo.backend.market.dto.SpreadStatsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class SpreadStatsService {

    private static final Logger log = LoggerFactory.getLogger(SpreadStatsService.class);
    private static final int DEFAULT_LIMIT = 60;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 1000;
    private static final BigDecimal WIDE_RATIO = new BigDecimal("1.5");
    private static final BigDecimal VERY_WIDE_RATIO = new BigDecimal("3.0");

    private final CurrencyPairRepository currencyPairRepository;
    private final MarketRateRepository marketRateRepository;
    private final MarketRateTickRepository marketRateTickRepository;

    public SpreadStatsService(
            CurrencyPairRepository currencyPairRepository,
            MarketRateRepository marketRateRepository,
            MarketRateTickRepository marketRateTickRepository
    ) {
        this.currencyPairRepository = currencyPairRepository;
        this.marketRateRepository = marketRateRepository;
        this.marketRateTickRepository = marketRateTickRepository;
    }

    public SpreadStatsResponse getSpreadStats(String currencyPairSymbol, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        CurrencyPair currencyPair = currencyPairRepository.findBySymbol(currencyPairSymbol)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Currency pair not found: " + currencyPairSymbol
                ));
        MarketRate latestRate = marketRateRepository.findByCurrencyPair(currencyPair)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Latest market rate not found: " + currencyPairSymbol
                ));

        List<MarketRateTick> ticks = marketRateTickRepository
                .findByCurrencyPair_SymbolOrderByQuotedAtDesc(
                        currencyPairSymbol,
                        PageRequest.of(0, normalizedLimit)
                );

        int sampleCount = ticks.size();
        Integer pipScale = currencyPair.getPipScale();
        BigDecimal averageSpread = calculateAverageSpread(ticks, currencyPair.getPriceScale());
        BigDecimal minSpread = ticks.stream()
                .map(MarketRateTick::getSpread)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        BigDecimal maxSpread = ticks.stream()
                .map(MarketRateTick::getSpread)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        SpreadStatus status = determineStatus(latestRate.getSpread(), averageSpread, sampleCount);

        return new SpreadStatsResponse(
                currencyPair.getSymbol(),
                latestRate.getBid(),
                latestRate.getAsk(),
                latestRate.getSpread(),
                toPips(latestRate.getSpread(), pipScale, currencyPair.getSymbol()),
                toPips(averageSpread, pipScale, currencyPair.getSymbol()),
                toPips(minSpread, pipScale, currencyPair.getSymbol()),
                toPips(maxSpread, pipScale, currencyPair.getSymbol()),
                status.name(),
                sampleCount,
                normalizedLimit,
                pipScale,
                latestRate.getQuotedAt()
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < MIN_LIMIT) {
            return MIN_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            return MAX_LIMIT;
        }
        return limit;
    }

    private BigDecimal calculateAverageSpread(List<MarketRateTick> ticks, Integer priceScale) {
        if (ticks.isEmpty()) {
            return null;
        }

        List<BigDecimal> spreads = ticks.stream()
                .map(MarketRateTick::getSpread)
                .filter(Objects::nonNull)
                .toList();
        if (spreads.isEmpty()) {
            return null;
        }

        BigDecimal sum = spreads.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int workingScale = Math.max(0, priceScale == null ? 0 : priceScale) + 6;
        return sum.divide(BigDecimal.valueOf(spreads.size()), workingScale, RoundingMode.HALF_UP);
    }

    private BigDecimal toPips(BigDecimal spread, Integer pipScale, String currencyPairSymbol) {
        if (spread == null) {
            return null;
        }
        if (pipScale == null || pipScale <= 0) {
            log.warn("Cannot convert spread to pips. currencyPair={}, pipScale={}", currencyPairSymbol, pipScale);
            return null;
        }
        return spread.movePointRight(pipScale).setScale(1, RoundingMode.HALF_UP);
    }

    private SpreadStatus determineStatus(BigDecimal currentSpread, BigDecimal averageSpread, int sampleCount) {
        if (sampleCount < 5 || currentSpread == null || averageSpread == null
                || averageSpread.compareTo(BigDecimal.ZERO) <= 0) {
            return SpreadStatus.INSUFFICIENT_DATA;
        }

        BigDecimal ratio = currentSpread.divide(averageSpread, 6, RoundingMode.HALF_UP);
        if (ratio.compareTo(WIDE_RATIO) <= 0) {
            return SpreadStatus.NORMAL;
        }
        if (ratio.compareTo(VERY_WIDE_RATIO) <= 0) {
            return SpreadStatus.WIDE;
        }
        return SpreadStatus.VERY_WIDE;
    }
}
