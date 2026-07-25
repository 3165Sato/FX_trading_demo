package com.example.fx.demo.backend.cash.api;

import com.example.fx.demo.backend.cash.dto.CashAmountRequest;
import com.example.fx.demo.backend.cash.dto.CashOperationResponse;
import com.example.fx.demo.backend.cash.dto.CashTransactionResponse;
import com.example.fx.demo.backend.cash.service.CashService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/trade/account")
public class CashController {

    private final CashService cashService;

    public CashController(CashService cashService) {
        this.cashService = cashService;
    }

    @PostMapping("/deposits")
    public CashOperationResponse deposit(@RequestBody CashAmountRequest request) {
        return cashService.deposit(request.amount());
    }

    @PostMapping("/withdrawals")
    public CashOperationResponse withdraw(@RequestBody CashAmountRequest request) {
        return cashService.withdraw(request.amount());
    }

    @GetMapping("/cash-transactions")
    public List<CashTransactionResponse> getCashTransactions(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return cashService.getTransactions(limit);
    }
}
