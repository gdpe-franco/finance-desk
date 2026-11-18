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
- Add focused unit or MySQL integration coverage for business rules and persistence behavior. Concurrency guarantees require concurrent integration tests against MySQL, not an in-memory substitute.
- Preserve unrelated or hand-edited work. Do not commit, amend, push, or create PRs unless requested.
- Run `./mvnw -B verify` and the documented Docker-based smoke check appropriate to the change. Report checks actually run and any environment blocker.
- Keep responses and documentation in English. Do not commit secrets, local `.env` files, or generated build output.
