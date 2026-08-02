package com.example.fx.demo.backend.position.service;

import com.example.fx.demo.backend.common.enums.OrderSource;
import com.example.fx.demo.backend.common.enums.QuickCloseScope;
import com.example.fx.demo.backend.position.dto.PositionCloseResponse;
import com.example.fx.demo.backend.position.dto.QuickCloseFailureResponse;
import com.example.fx.demo.backend.position.dto.QuickCloseRequest;
import com.example.fx.demo.backend.position.dto.QuickCloseResponse;
import com.example.fx.demo.backend.position.model.QuickCloseTarget;
import com.example.fx.demo.backend.trade.config.DemoTradingAccountInitializer;
import com.example.fx.demo.backend.trade.service.AccountTradeLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuickCloseService {

    private static final Logger log = LoggerFactory.getLogger(QuickCloseService.class);
    private static final String GENERIC_FAILURE_REASON = "決済処理に失敗しました。";

    private final AccountTradeLockService accountTradeLockService;
    private final PositionService positionService;

    public QuickCloseService(
            AccountTradeLockService accountTradeLockService,
            PositionService positionService
    ) {
        this.accountTradeLockService = accountTradeLockService;
        this.positionService = positionService;
    }

    public QuickCloseResponse quickClose(QuickCloseRequest request) {
        validateRequest(request);
        return accountTradeLockService.withAccountLock(
                DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER,
                () -> quickCloseForLockedAccount(request)
        );
    }

    private QuickCloseResponse quickCloseForLockedAccount(QuickCloseRequest request) {
        List<QuickCloseTarget> targets = positionService.findOpenQuickCloseTargetsForLockedAccount(
                request.scope(),
                request.currencyPair()
        );
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "対象となるOPEN建玉がありません。");
        }

        List<PositionCloseResponse> successes = new ArrayList<>();
        List<QuickCloseFailureResponse> failures = new ArrayList<>();
        for (QuickCloseTarget target : targets) {
            try {
                successes.add(positionService.closePositionForLockedAccount(
                        target.positionId(),
                        OrderSource.QUICK_CLOSE
                ));
            } catch (ResponseStatusException exception) {
                failures.add(new QuickCloseFailureResponse(
                        target.positionId(),
                        target.currencyPair(),
                        failureReason(exception)
                ));
            } catch (RuntimeException exception) {
                log.error("Quick close failed for position {}", target.positionId(), exception);
                failures.add(new QuickCloseFailureResponse(
                        target.positionId(),
                        target.currencyPair(),
                        GENERIC_FAILURE_REASON
                ));
            }
        }

        return new QuickCloseResponse(
                request.scope(),
                request.scope() == QuickCloseScope.PAIR ? request.currencyPair() : null,
                targets.size(),
                successes.size(),
                failures.size(),
                List.copyOf(successes),
                List.copyOf(failures)
        );
    }

    private void validateRequest(QuickCloseRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request is required");
        }
        if (request.scope() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope is required");
        }
        if (request.scope() == QuickCloseScope.PAIR
                && (request.currencyPair() == null || request.currencyPair().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyPair is required for PAIR scope");
        }
        if (request.scope() == QuickCloseScope.ACCOUNT && request.currencyPair() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyPair is not allowed for ACCOUNT scope");
        }
    }

    private String failureReason(ResponseStatusException exception) {
        return exception.getReason() == null || exception.getReason().isBlank()
                ? GENERIC_FAILURE_REASON
                : exception.getReason();
    }
}
