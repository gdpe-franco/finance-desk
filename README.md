# Internal Transactions System

A small backend-only transaction processing project built with Java 21, Spring Boot 3, and MySQL 8.

## Run locally

Prerequisites: Java 21 and Docker Compose. Maven is provided by the project wrapper.

```sh
docker compose up -d --wait
./mvnw spring-boot:run
```

The API runs at `http://localhost:8080`; health is available at `http://localhost:8080/actuator/health`.

Local database credentials have development-only defaults in `compose.yaml`. To override them, copy `.env.example` to `.env`. To stop MySQL while preserving its data, run `docker compose down`; to delete the local database volume as well, run `docker compose down -v`.

Run the test suite with:

```sh
./mvnw -B verify
```

Tests connect to the MySQL service described above. The test dependencies also include Testcontainers for isolated MySQL integration tests as those are introduced.

## Project structure

Code is organized under `io.github.gdpe_franco.internaltransactions` into domain, application, infrastructure, and web packages. See [ARCHITECTURE.md](ARCHITECTURE.md) for the layer boundaries and the transfer, money, idempotency, and concurrency decisions.
