package com.example.fx.demo.backend.account;

import com.example.fx.demo.backend.account.dto.AccountSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade/account")
public class AccountSummaryController {

    private final AccountSummaryService accountSummaryService;

    public AccountSummaryController(AccountSummaryService accountSummaryService) {
        this.accountSummaryService = accountSummaryService;
    }

    @GetMapping("/summary")
    public AccountSummaryResponse getSummary() {
        return accountSummaryService.getDefaultAccountSummary();
    }
}
