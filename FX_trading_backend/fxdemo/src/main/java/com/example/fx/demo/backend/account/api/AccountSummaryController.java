package com.example.fx.demo.backend.account.api;

import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import com.example.fx.demo.backend.account.dto.EquitySnapshotResponse;
import com.example.fx.demo.backend.account.service.AccountSummaryService;
import com.example.fx.demo.backend.account.service.EquitySnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/trade/account")
public class AccountSummaryController {

    private final AccountSummaryService accountSummaryService;
    private final EquitySnapshotService equitySnapshotService;

    public AccountSummaryController(
            AccountSummaryService accountSummaryService,
            EquitySnapshotService equitySnapshotService
    ) {
        this.accountSummaryService = accountSummaryService;
        this.equitySnapshotService = equitySnapshotService;
    }

    @GetMapping("/summary")
    public AccountSummaryResponse getSummary() {
        return accountSummaryService.getDefaultAccountSummary();
    }

    @GetMapping("/equity-history")
    public List<EquitySnapshotResponse> getEquityHistory(
            @RequestParam(defaultValue = "300") int limit,
            @RequestParam(required = false) Instant from
    ) {
        return equitySnapshotService.getDefaultAccountHistory(limit, from);
    }
}
