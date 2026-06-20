package com.example.fx.demo.backend.trade;

import com.example.fx.demo.backend.account.Account;
import com.example.fx.demo.backend.account.AccountRepository;
import com.example.fx.demo.backend.customer.Customer;
import com.example.fx.demo.backend.customer.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class DemoTradingAccountInitializer implements CommandLineRunner {

    public static final String DEFAULT_CUSTOMER_NUMBER = "DEMO-CUSTOMER-001";
    public static final String DEFAULT_ACCOUNT_NUMBER = "DEMO-ACCOUNT-001";

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public DemoTradingAccountInitializer(
            CustomerRepository customerRepository,
            AccountRepository accountRepository
    ) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
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

        accountRepository.findByAccountNumber(DEFAULT_ACCOUNT_NUMBER)
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setCustomerId(customer.getId());
                    account.setAccountNumber(DEFAULT_ACCOUNT_NUMBER);
                    account.setUserName("demo-trader");
                    account.setBalance(new BigDecimal("1000000"));
                    account.setMarginUsed(BigDecimal.ZERO);
                    account.setRealizedPnl(BigDecimal.ZERO);
                    account.setStatus("ACTIVE");
                    return accountRepository.save(account);
                });
    }
}
