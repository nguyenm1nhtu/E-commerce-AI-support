# Repository Guidelines

## Project Structure & Module Organization

This repository is a Spring Boot Maven application. Production code is under
`src/main/java/com/ecommerce/aicommercesupport/`; application configuration is
under `src/main/resources/`. Tests mirror the Java package structure under
`src/test/java/`. Project documentation belongs in `docs/` (for example,
`docs/context.md`). The Maven build is defined in `pom.xml`; use the checked-in
Maven Wrapper (`mvnw` or `mvnw.cmd`) for reproducible commands.

Use English throughout repository content, including `AGENTS.md`, code
comments, and configuration comments. Only documentation under `docs/` and
README files should use Vietnamese, unless the user explicitly requests
otherwise. Preserve technical names, code identifiers, paths, commands, and
sample data where needed for accuracy.

Within `docs/`, only `context.md`, `docker-compose.md`, and
`terminal-commands.md` may be committed or pushed to Git. All other documents
in this directory, including future generated documents, must be excluded by
`.gitignore` and kept local. Do not use `git add -f` to track those documents.

Organize Java packages by feature module under `com.ecommerce.aicommercesupport`
(for example, `order`, `payment`, and `shipment`), then by layer inside each
module: `entity` for JPA entities and their domain enums, `repository` for
Spring Data repositories, `dto` for request/response objects, `controller` for
HTTP controllers, and `service` for application/business logic. Examples:
`order.entity.Order`, `order.repository.OrderRepository`, and
`payment.service.PaymentService`. Shared code belongs under `common`, grouped
by responsibility (for example, `common.exception` and `common.controller`).
Do not group all modules into top-level layer packages. Create packages when
they contain actual classes; tests should mirror the module and layer layout.

## Build, Test, and Development Commands

- `./mvnw clean verify` (Linux/macOS) or `./mvnw.cmd clean verify` (Windows):
  compile the project and run the full verification lifecycle.
- `./mvnw test` or `./mvnw.cmd test`: run the JUnit test suite.
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (Linux/macOS) or
  `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"` (Windows):
  start the application with the dev profile after providing the required
  database environment variables. See `docs/terminal-commands.md`.

The project targets Java 25. Keep a compatible JDK configured before invoking
the wrapper. Do not commit generated `target/` output.

Liquibase owns schema changes. Add immutable changesets under
`src/main/resources/db/changelog/changes/` and include them from
`src/main/resources/db/changelog/db.changelog-master.yaml`. Hibernate runs with
`ddl-auto: validate`; entity changes require matching migrations. Tests use the
`test` profile and H2.

For entity-driven schema changes, generate SQL migrations with the configured
Liquibase Hibernate integration instead of writing the initial DDL manually:
`./scripts/New-Migration.ps1 <change-name-in-kebab-case>`.
The script selects the next numeric prefix, generates SQL through Maven, saves
it under `src/main/resources/db/changelog/changes/`, and adds the master include
only when generation succeeds with changesets. Use a development database at
the existing migration baseline. Review the generated diff, keep only the
intended schema changes, then run `./mvnw.cmd compile liquibase:updateSQL`,
`./mvnw.cmd liquibase:update`, and `./mvnw.cmd test`.
Do not manually number files or append master includes for generated migrations.
Do not automatically apply generated drops or
interpret a rename as drop-and-create. Hand-edit only to correct generated SQL
or cover changes the generator cannot represent, and explain those adjustments.
If generation is blocked, report the blocker rather than silently writing the
migration manually. Keep runtime data operations in Hibernate/JPA and retain
`ddl-auto: validate`.

## Coding Style & Naming Conventions

Use four-space indentation for Java and retain the existing package
`com.ecommerce.aicommercesupport`. Classes use `PascalCase`, methods and local
variables use `camelCase`, and constants use `UPPER_SNAKE_CASE`. Keep classes
focused and place new tests in the matching package. No repository-level
formatter or linter is configured; follow the surrounding Spring and Java
style and keep imports and formatting clean.

## Cache Guidelines

Proactively apply Redis caching to read APIs when data is frequently reread,
changes infrequently, and can tolerate delayed updates; do not cache every API
indiscriminately. Whenever caching is added or changed, tell the user which
endpoints/methods changed, the cache keys, TTLs, and freshness limitations;
update `docs/context.md`.

- Cache DTOs, not JPA entities, credentials, or tokens. Set finite TTLs.
- Check authentication and ownership/authorization before reading the cache,
  including on cache hits; do not cache authorization decisions.
- Keys must distinguish resources, users/tenants when data depends on the
  viewer, and filters/pages/sort orders for queried or paginated lists.
- Do not cache `404`/`403` errors; empty lists for existing resources may be cached.
- When adding write APIs, evict affected detail, list, and aggregate caches
  after transaction commit. If no write flow exists yet, document the maximum
  staleness allowed by the TTL and that direct database updates do not evict caches.
- Test cache hits/misses, data isolation, authorization on existing cache entries,
  and reloading after eviction/expiration. Verify JSON round-trips and TTLs with
  real Redis when adding new cached data types, especially collections or pages.
- Do not use the cache as a session/refresh-token store. Do not use `FLUSHDB`
  or `FLUSHALL` to clear caches on Redis instances holding authentication data.

## Testing Guidelines

Tests use JUnit 5 with Spring Boot test support. Name test classes with the
`*Tests` suffix and test methods descriptively, such as `contextLoads()`.
Add or update tests for every behavior change. Run `./mvnw test` before opening
a pull request; no explicit coverage threshold is currently configured.

## Commit & Pull Request Guidelines

Each commit must address exactly one logical change. Split unrelated features,
fixes, documentation tasks, and refactors into separate commits before pushing.
Tests and documentation directly supporting the same change belong with it.
Never bundle unrelated work merely to reduce the number of commits.

Recent commits use short, imperative summaries (for example, `Fix ...` or
`Normalize ...`). Keep commits focused and messages concise. Open pull
requests against `dev`, explain the behavior or files changed, include test
commands and results, and link related issues when applicable. Add screenshots
only when a change affects a user-facing interface.

## Security & Configuration Tips

Keep credentials, tokens, and environment-specific secrets out of Git.
Review `src/main/resources/application.yml` and its dev/prod profile files;
use environment configuration for local or deployment-specific values.
