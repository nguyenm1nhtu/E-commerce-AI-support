# Commerce read API

Controllers are grouped by module: `order.controller.OrderController`,
`order.controller.OrderItemController`, `payment.controller.PaymentController`,
and `shipment.controller.ShipmentController`.

## Authentication and ownership

All endpoints require authentication through the existing Spring Security
configuration (HTTP Basic or a logged-in session). The identity module and JWT
authentication are not implemented yet.

`common.security.CurrentUserId` currently interprets the authenticated
principal's name as the user's UUID. For local testing with Spring Boot's
default user, set `SPRING_SECURITY_USER_NAME` to the UUID stored in
`orders.user_id` and supply `SPRING_SECURITY_USER_PASSWORD` through the
environment. The default username `user` does not identify an order owner and
receives `403 Forbidden`. Update this adapter when the identity module supplies
a dedicated user principal.

The controllers never take the acting user's ID from a request parameter.
Each order and child-resource lookup checks the order's owner. An order that
belongs to another user returns `404`, as does a missing order. These endpoints
are for customer access; no support-agent override is implemented.

## Endpoints

| Method | Path | Response |
| --- | --- | --- |
| GET | `/api/orders` | Current user's orders as `{content, page}` |
| GET | `/api/orders/{orderId}` | `OrderDetailDto`: `{order, items, payment, shipment}` |
| GET | `/api/orders/{orderId}/items` | Array of `OrderItemDto` |
| GET | `/api/orders/{orderId}/items/{itemId}` | `OrderItemDto` belonging to this order |
| GET | `/api/orders/{orderId}/payment` | `PaymentDto` |
| GET | `/api/orders/{orderId}/shipment` | `ShipmentDto` |

The order list accepts zero-based `page`, `size`, and `sort`, for example:
`/api/orders?page=0&size=20&sort=orderedAt,desc`.
Defaults are page 0, size 20, and descending `orderedAt` then `id`.
Allowed sort fields are `id`, `orderedAt`, `status`, and `totalAmount`.
Page metadata contains `size`, `number`, `totalElements`, and `totalPages`.

An existing order without items returns an empty item list. Order details
allow `payment` and `shipment` to be null if these records do not exist yet.
Their dedicated endpoints return `404` when the respective record is absent.
No write endpoints are provided by these read services.

## Errors and verification

`common.exception.GlobalExceptionHandle` maps controller errors to
`common.dto.ApiErrorResponse` using `application/json`:

```json
{
  "timestamp": "2026-10-03T04:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Order not found: 8b3e38bb-4e2e-49cf-aa94-1d733fa69365",
  "path": "/api/orders/8b3e38bb-4e2e-49cf-aa94-1d733fa69365",
  "errors": {}
}
```

`ResourceNotFoundException` returns `404`. Malformed UUID parameters, malformed
JSON, body validation failures, and unsupported sort fields return `400`.
Validation failures populate `errors` with field/object names and validation
messages; other errors use an empty map. `ResponseStatusException` retains its
HTTP status and its reason for client errors. Framework HTTP headers such as
`Allow` on `405` responses are preserved.

Unexpected exceptions return `500` with a generic message; details are logged
on the server. Server errors never return the exception message in the body.
Controller-level authentication and access-denied exceptions return `401` and
`403`. Unauthenticated JSON requests rejected by the Spring Security filter
still return `401` through its existing entry point, outside this MVC advice.

`GlobalExceptionHandleTests` verifies the JSON contract, validation failures,
malformed input, preserved status/headers, and suppression of internal details.

Run `.\mvnw.cmd test` on Windows. `CommerceControllerTests` exercises real
controllers, services, repositories, and H2 through MockMvc, covering successful
responses, pagination, ownership, authentication, missing data, and malformed
requests.

## Swagger UI

The application includes `springdoc-openapi-starter-webmvc-ui` 3.1.1, from the
[Spring Boot 4 compatible springdoc line](https://springdoc.org/faq.html).
Start the application with the database and authentication environment variables
described above, then open <http://localhost:8080/swagger-ui.html>.
The OpenAPI document is available at `/v3/api-docs`. Documentation is public;
the commerce endpoints still require authentication.

1. Click **Authorize**, enter the UUID username and configured password, then close
   the dialog.
2. Expand an endpoint and click **Try it out**. For the order list, use `page=0`,
   `size=20`, and `sort=orderedAt,desc`.
3. Use IDs from your own database for `orderId` and `itemId`, then click **Execute**.
   All six endpoints should return `200` when the corresponding data exists.
4. Replace `orderId` with an unused UUID: order details and all nested endpoints
   return `404`. An unused `itemId` under an existing order also returns `404`.
5. For an existing order without related records, the items endpoint returns `[]`;
   payment, shipment, and individual item lookups return `404`. A user without
   orders receives an empty page with `200`.

`OpenApiTests` checks the UI redirect, HTML, configuration, Basic authentication
scheme, all six documented operations, success/error responses, and API protection.

## Migration from an empty database

`EmptyDatabaseMigrationTests` starts with a fresh H2 database on every run,
verifies the four commerce tables and Liquibase history, then runs migration
again to check that changesets are not reapplied and the lock is released.
Spring integration tests also validate the migrated schema against JPA entities
with `ddl-auto: validate`.

H2 does not execute the PostgreSQL-only pgvector changeset. To verify that as
well, provide a **dedicated empty PostgreSQL database** on a server with pgvector
installed, and a database user allowed to create the extension:

```powershell
$env:MIGRATION_TEST_URL = 'jdbc:postgresql://localhost:5432/commerce_migration_test'
$env:MIGRATION_TEST_USER = 'migration_test_user'
# Set MIGRATION_TEST_PASSWORD securely in this terminal's environment.
.\mvnw.cmd '-Dtest=EmptyDatabaseMigrationTests' test
Remove-Item Env:MIGRATION_TEST_URL, Env:MIGRATION_TEST_USER, Env:MIGRATION_TEST_PASSWORD -ErrorAction SilentlyContinue
```

The PostgreSQL test refuses a database that already has tables in `public`.
It leaves the migrated schema in place, so use a new empty database on the next
run. Without `MIGRATION_TEST_URL`, this test is explicitly skipped; the H2 test
still runs. Do not point this test at the development or production database.

### Verification recorded on 2026-10-03

- `.\mvnw.cmd test`: 52 tests, 0 failures, 0 errors; 51 passed and the optional
  PostgreSQL test was skipped in this default run.
- `.\mvnw.cmd '-Dtest=EmptyDatabaseMigrationTests' test` with the migration
  environment variables: both H2 and PostgreSQL tests passed, with no skips.
  PostgreSQL ran in an isolated `pgvector/pgvector:pg17` container. All three
  changesets applied from an empty database, pgvector was present, and a second
  migration applied no duplicate changesets.
- Swagger UI was exercised in Microsoft Edge through **Authorize**, **Try it out**,
  and **Execute** against the application connected to that PostgreSQL database.
  All six endpoints returned `200` with test data. The five endpoints accepting
  `orderId` returned `404` for a missing order; the individual item endpoint also
  returned `404` for a missing item under an existing order.
- Application startup against the migrated PostgreSQL schema passed Hibernate
  validation. Test records and the temporary container were removed afterward.
