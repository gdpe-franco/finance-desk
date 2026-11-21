package io.github.gdpe_franco.internaltransactions;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "Finance Desk API",
        version = "v1",
        description = "Backend API for internal accounts, transfers, and ledger entries."))
public class FinanceDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceDeskApplication.class, args);
    }
}
