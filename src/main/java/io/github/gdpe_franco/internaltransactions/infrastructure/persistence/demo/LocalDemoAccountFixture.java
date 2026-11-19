package io.github.gdpe_franco.internaltransactions.infrastructure.persistence.demo;

import io.github.gdpe_franco.internaltransactions.domain.account.ClabeAccountNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Profile("local-demo")
class LocalDemoAccountFixture implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalDemoAccountFixture.class);

    private final JdbcTemplate jdbcTemplate;

    LocalDemoAccountFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class) != 0) {
            log.info("Local demo accounts were not seeded because the database already contains accounts");
            return;
        }

        var sourceAccountNumber = insertAccount("Demo Source", new BigDecimal("10000.00"));
        var destinationAccountNumber = insertAccount("Demo Destination", new BigDecimal("1000.00"));
        var sourceId = accountId(sourceAccountNumber);
        var destinationId = accountId(destinationAccountNumber);
        log.info("Local demo accounts ready: source ID {}, destination ID {}", sourceId, destinationId);
    }

    private String insertAccount(String ownerName, BigDecimal balance) {
        var accountNumber = ClabeAccountNumber.generate();
        jdbcTemplate.update("""
                        INSERT INTO accounts (account_number, owner_name, currency, balance, status)
                        VALUES (?, ?, 'MXN', ?, 'ACTIVE')
                        """, accountNumber, ownerName, balance);
        return accountNumber;
    }

    private long accountId(String accountNumber) {
        return jdbcTemplate.queryForObject("SELECT id FROM accounts WHERE account_number = ?", Long.class,
                accountNumber);
    }
}
