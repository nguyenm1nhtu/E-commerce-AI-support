# Repository Guidelines

## Project Structure & Module Organization

This repository is a Spring Boot Maven application. Production code is under
`src/main/java/com/ecommerce/aicommercesupport/`; application configuration is
under `src/main/resources/`. Tests mirror the Java package structure under
`src/test/java/`. Project documentation belongs in `docs/` (for example,
`docs/context.md`). The Maven build is defined in `pom.xml`; use the checked-in
Maven Wrapper (`mvnw` or `mvnw.cmd`) for reproducible commands.

## Build, Test, and Development Commands

- `./mvnw clean verify` (Linux/macOS) or `./mvnw.cmd clean verify` (Windows):
  compile the project and run the full verification lifecycle.
- `./mvnw test` or `./mvnw.cmd test`: run the JUnit test suite.
- `./mvnw spring-boot:run` or `./mvnw.cmd spring-boot:run`: start the local
  Spring Boot application.

The project targets Java 25. Keep a compatible JDK configured before invoking
the wrapper. Do not commit generated `target/` output.

## Coding Style & Naming Conventions

Use four-space indentation for Java and retain the existing package
`com.ecommerce.aicommercesupport`. Classes use `PascalCase`, methods and local
variables use `camelCase`, and constants use `UPPER_SNAKE_CASE`. Keep classes
focused and place new tests in the matching package. No repository-level
formatter or linter is configured; follow the surrounding Spring and Java
style and keep imports and formatting clean.

## Testing Guidelines

Tests use JUnit 5 with Spring Boot test support. Name test classes with the
`*Tests` suffix and test methods descriptively, such as `contextLoads()`.
Add or update tests for every behavior change. Run `./mvnw test` before opening
a pull request; no explicit coverage threshold is currently configured.

## Commit & Pull Request Guidelines

Recent commits use short, imperative summaries (for example, `Fix ...` or
`Normalize ...`). Keep commits focused and messages concise. Open pull
requests against `dev`, explain the behavior or files changed, include test
commands and results, and link related issues when applicable. Add screenshots
only when a change affects a user-facing interface.

## Security & Configuration Tips

Keep credentials, tokens, and environment-specific secrets out of Git.
Review `src/main/resources/application.properties` and use environment
configuration for local or deployment-specific values.
