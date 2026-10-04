# Repository Guidelines

## Project Structure & Module Organization

This repository is a Spring Boot Maven application. Production code is under
`src/main/java/com/ecommerce/aicommercesupport/`; application configuration is
under `src/main/resources/`. Tests mirror the Java package structure under
`src/test/java/`. Project documentation belongs in `docs/` (for example,
`docs/context.md`). The Maven build is defined in `pom.xml`; use the checked-in
Maven Wrapper (`mvnw` or `mvnw.cmd`) for reproducible commands.

Tài liệu trong thư mục `docs/` phải viết bằng tiếng Việt, trừ khi người dùng
yêu cầu rõ ràng viết bằng tiếng Anh. Giữ nguyên tên kỹ thuật, định danh trong
code, đường dẫn, lệnh và dữ liệu mẫu khi cần để bảo đảm tính chính xác.

Trong `docs/`, chỉ được đưa `context.md`, `docker-compose.md` và
`terminal-commands.md` vào Git để commit/push. Mọi tài liệu khác trong thư mục
này, kể cả tài liệu sinh ra sau này, phải được `.gitignore` loại trừ và chỉ
lưu cục bộ. Không dùng `git add -f` để đưa các tài liệu đó vào Git.

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

## Coding Style & Naming Conventions

Use four-space indentation for Java and retain the existing package
`com.ecommerce.aicommercesupport`. Classes use `PascalCase`, methods and local
variables use `camelCase`, and constants use `UPPER_SNAKE_CASE`. Keep classes
focused and place new tests in the matching package. No repository-level
formatter or linter is configured; follow the surrounding Spring and Java
style and keep imports and formatting clean.

## Cache Guidelines

Chủ động áp dụng Redis cache cho API đọc khi dữ liệu thường được đọc lại, ít
thay đổi và chấp nhận được độ trễ cập nhật; không cache mọi API một cách máy móc.
Mỗi lần thêm hoặc đổi cache, phải báo rõ cho người dùng endpoint/method đã đổi,
cache key, TTL và giới hạn về độ mới của dữ liệu; cập nhật `docs/context.md`.

- Dùng DTO, không cache entity JPA, credentials hoặc token. Đặt TTL hữu hạn.
- Kiểm tra authentication và ownership/authorization trước khi đọc cache,
  kể cả cache hit; không cache kết quả kiểm tra quyền.
- Key phải phân biệt đầy đủ resource, user/tenant nếu dữ liệu phụ thuộc người
  xem, và filter/page/sort nếu cache danh sách có truy vấn hoặc phân trang.
- Không cache lỗi `404`/`403`; có thể cache danh sách rỗng của resource tồn tại.
- Khi thêm API ghi, evict các cache bị ảnh hưởng sau transaction commit, gồm
  cache chi tiết, danh sách và dữ liệu tổng hợp. Nếu chưa có luồng ghi, ghi rõ
  giới hạn stale theo TTL và việc cập nhật database trực tiếp không tự evict.
- Test cache hit/miss, cách ly dữ liệu và quyền truy cập trên cache đã có sẵn,
  reload sau eviction/hết hạn; kiểm tra JSON round-trip và TTL với Redis thật
  khi thêm kiểu dữ liệu cache mới, đặc biệt collection hoặc dữ liệu phân trang.
- Không dùng cache làm kho phiên/refresh token, không dùng `FLUSHDB` hoặc
  `FLUSHALL` để xóa cache trên Redis đang giữ dữ liệu auth.

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
Review `src/main/resources/application.yml` and its dev/prod profile files;
use environment configuration for local or deployment-specific values.
