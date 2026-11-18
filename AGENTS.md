# Project instructions

## Context and planning

- Read `ARCHITECTURE.md` before changing product behavior or technical direction. It owns accepted design decisions; promote durable decisions there instead of duplicating them here.
- Read the relevant approved phase brief in `.planning/prds/` before implementing a new feature phase. If one is missing, ask before reconstructing requirements from memory. Small fixes do not need a PRD.
- Load `prd-writing` when writing or reviewing a PRD, and `data-modeling` when designing or reviewing schema, constraints, indexes, or migrations.
- Keep the MVP a Java 21 / Spring Boot 3 monolith using MySQL and Flyway. Do not add brokers, caches, services, or infrastructure outside the accepted architecture.
- Keep onboarding in `README.md`, technical decisions in `ARCHITECTURE.md`, and local planning artifacts under `.planning/`.

## Implementation

- Load `ponytail` before coding, at full level unless the user says otherwise.
- Follow the established package boundaries: domain and application code must not depend on web or JPA implementation classes.
- Use `BigDecimal` for money; never use floating-point monetary types or silently round amounts. Preserve transaction boundaries, idempotency, ledger immutability, and deterministic account lock ordering.
- Keep Flyway migrations authoritative; Hibernate schema generation stays on `validate`. Never edit a migration that may have been applied; add a new migration.
- Test domain rules with plain JUnit unit tests (no Spring or database); test HTTP and persistence behavior with MySQL-backed integration tests; test transfer atomicity and concurrency with real MySQL transactions and concurrent operations.
- Keep a clear happy-path test, and use JUnit 5 `@ParameterizedTest` with `@ValueSource`, `@CsvSource`, or `@MethodSource` for input variants that share the same behavior and expected outcome. Keep distinct outcomes or business rules in separate tests.
- Preserve unrelated or hand-edited work. Do not commit, amend, push, or create PRs unless requested.
- Run `./mvnw -B verify` and the documented Docker-based smoke check appropriate to the change. Report checks actually run and any environment blocker.
- Keep responses and documentation in English. Do not commit secrets, local `.env` files, or generated build output.
