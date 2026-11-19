# Internal Transactions System

A small backend-only transaction processing project built with Java 21, Spring Boot 3, and MySQL 8.

## Run locally

Prerequisites: Java 21 and Docker Compose. Maven is provided by the project wrapper.

```sh
docker compose up -d --wait
./mvnw spring-boot:run
```

The API runs at `http://localhost:8080`; health is available at `http://localhost:8080/actuator/health`.

For funded demo accounts, run the service with the explicit `local-demo` profile:

```sh
SPRING_PROFILES_ACTIVE=local-demo ./mvnw spring-boot:run
```

The fixture seeds two MXN accounts only when the database has no accounts and logs their IDs. Their opening balances are demo-only and are not recorded in the transfer ledger.

Local database credentials have development-only defaults in `compose.yaml`. To override them, copy `.env.example` to `.env`. To stop MySQL while preserving its data, run `docker compose down`; to delete the local database volume as well, run `docker compose down -v`.

Run the test suite with:

```sh
./mvnw -B verify
```

Tests connect to the MySQL service described above. The test dependencies also include Testcontainers for isolated MySQL integration tests as those are introduced.

## Project structure

Code is organized under `io.github.gdpe_franco.internaltransactions` into domain, application, infrastructure, and web packages. See [ARCHITECTURE.md](ARCHITECTURE.md) for the layer boundaries and the transfer, money, idempotency, and concurrency decisions.

Account numbers use an 18-digit synthetic CLABE format for this demo only. They are not bank-issued and cannot be used for SPEI or external payments.
