package com.example.fx.demo.backend.trade.config;

import com.example.fx.demo.backend.account.domain.Account;
import com.example.fx.demo.backend.account.repository.AccountRepository;
import com.example.fx.demo.backend.cash.domain.CashTransaction;
import com.example.fx.demo.backend.cash.repository.CashTransactionRepository;
import com.example.fx.demo.backend.common.enums.CashTransactionStatus;
import com.example.fx.demo.backend.common.enums.CashTransactionType;
import com.example.fx.demo.backend.customer.domain.Customer;
import com.example.fx.demo.backend.customer.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DemoTradingAccountInitializer implements CommandLineRunner {

    public static final String DEFAULT_CUSTOMER_NUMBER = "DEMO-CUSTOMER-001";
    public static final String DEFAULT_ACCOUNT_NUMBER = "DEMO-ACCOUNT-001";
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000000");

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final CashTransactionRepository cashTransactionRepository;

    public DemoTradingAccountInitializer(
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            CashTransactionRepository cashTransactionRepository
    ) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.cashTransactionRepository = cashTransactionRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Customer customer = customerRepository.findByCustomerNumber(DEFAULT_CUSTOMER_NUMBER)
                .orElseGet(() -> {
                    Customer next = new Customer();
                    next.setCustomerNumber(DEFAULT_CUSTOMER_NUMBER);
                    next.setLastName("Demo");
                    next.setFirstName("Trader");
                    next.setLastNameKana("Demo");
                    next.setFirstNameKana("Trader");
                    next.setBirthDate(LocalDate.of(1990, 1, 1));
                    next.setGender("UNSPECIFIED");
                    next.setPostalCode("000-0000");
                    next.setPrefecture("Tokyo");
                    next.setCity("Demo City");
                    next.setAddressLine1("Learning Demo Address");
                    next.setPhoneNumber("000-0000-0000");
                    next.setEmail("demo-trader@example.invalid");
                    next.setStatus("ACTIVE");
                    return customerRepository.save(next);
                });

        Account account = accountRepository.findByAccountNumber(DEFAULT_ACCOUNT_NUMBER)
                .orElseGet(() -> {
                    Account newAccount = new Account();
                    newAccount.setCustomerId(customer.getId());
                    newAccount.setAccountNumber(DEFAULT_ACCOUNT_NUMBER);
                    newAccount.setUserName("demo-trader");
                    newAccount.setBalance(INITIAL_BALANCE);
                    newAccount.setMarginUsed(BigDecimal.ZERO);
                    newAccount.setRealizedPnl(BigDecimal.ZERO);
                    newAccount.setStatus("ACTIVE");
                    return accountRepository.save(newAccount);
                });

        if (!cashTransactionRepository.existsByAccountId(account.getId())) {
            LocalDateTime recordedAt = account.getCreatedAt() == null ? LocalDateTime.now() : account.getCreatedAt();
            CashTransaction initialDeposit = new CashTransaction();
            initialDeposit.setAccountId(account.getId());
            initialDeposit.setTransactionType(CashTransactionType.DEPOSIT);
            initialDeposit.setAmount(INITIAL_BALANCE);
            initialDeposit.setStatus(CashTransactionStatus.COMPLETED);
            initialDeposit.setRequestedAt(recordedAt);
            initialDeposit.setCompletedAt(recordedAt);
            cashTransactionRepository.save(initialDeposit);
        }
    }
}
