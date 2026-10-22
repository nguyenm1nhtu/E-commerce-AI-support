# E-commerce AI Support

Backend Spring Boot cho nền tảng thương mại điện tử và hỗ trợ khách hàng. Nhánh `dev` hiện đã có xác thực JWT, CRUD commerce và luồng ticket hỗ trợ khách hàng/nhân viên.

## Tiến độ hiện tại

- **Authentication:** đăng ký, đăng nhập, cấp access token JWT, refresh token qua cookie `HttpOnly`, xoay vòng và thu hồi phiên refresh.
- **Orders và order items:** đọc, tạo, cập nhật, xóa từng resource và xóa toàn bộ resource thuộc customer hiện tại.
- **Payments và shipments:** đọc theo order, tạo, cập nhật, xóa từng resource và xóa toàn bộ resource thuộc customer hiện tại.
- **Ticketing:** customer tạo ticket, xem danh sách/chi tiết, trao đổi message, yêu cầu nhân viên hỗ trợ và xác nhận giải quyết. Support agent xem hàng đợi escalation, nhận ticket, xem ticket được giao, trả lời và đổi priority.
- **Bảo mật:** API nghiệp vụ yêu cầu Bearer access JWT. Quyền sở hữu resource được kiểm tra theo user đăng nhập; ticket cũng kiểm tra role và assignee. API auth dùng CSRF protection.
- **Cache:** Redis cache cho một số API đọc order/payment/shipment; các API ghi evict cache liên quan sau transaction commit.
- **Swagger/OpenAPI:** tài liệu API được cung cấp tại `/swagger-ui/index.html` khi ứng dụng chạy.

Các API CRUD commerce phục vụ kiểm thử và thao tác dữ liệu cơ bản; chưa tích hợp cổng thanh toán hoặc nhà vận chuyển. Ticket workflow đã có state transition và REST API, nhưng chưa kết nối LLM, chưa chạy AI background job hoặc agent orchestration.

## Công nghệ

- Java 25, Spring Boot 4, Spring MVC, Spring Security, OAuth2 Resource Server
- PostgreSQL, Spring Data JPA, Liquibase
- Redis cache
- Springdoc OpenAPI
- JUnit 5, Spring Boot Test, H2 cho kiểm thử

## Chạy ứng dụng

Yêu cầu JDK 25, Docker Compose, PostgreSQL và Redis. Trong PowerShell, chuẩn bị `.env.dev` theo `.env.example`, rồi chạy:

```powershell
.\scripts\dev.ps1
```

Script khởi động PostgreSQL/Redis bằng Docker Compose và chạy backend với profile `dev`. Backend mặc định ở `http://localhost:8080`; Swagger UI ở `http://localhost:8080/swagger-ui/index.html`.

Chi tiết cấu hình và lệnh chạy xem [docs/terminal-commands.md](docs/terminal-commands.md).

## Kiểm thử

```powershell
.\mvnw.cmd test
```

Lần kiểm tra đầy đủ gần nhất trên phần ticket và commerce đã merge vào `dev` dùng `clean test`: **299 tests, 0 failures, 22 skipped**. Một số integration tests cần Redis thật được skip nếu không bật cấu hình Redis integration tương ứng.

## API chính

| Nhóm | Endpoint tiêu biểu |
| --- | --- |
| Auth | `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` |
| Orders | `/api/orders`, `/api/orders/{orderId}` |
| Order items | `/api/orders/{orderId}/items`, `/api/order-items` |
| Payment | `/api/orders/{orderId}/payment`, `/api/payments` |
| Shipment | `/api/orders/{orderId}/shipment`, `/api/shipments` |
| Customer tickets | `/api/tickets`, `/api/tickets/{ticketId}/messages` |
| Support tickets | `/api/support/tickets`, `/api/support/tickets/escalations` |

Xem danh sách method, request/response và quyền truy cập đầy đủ trong Swagger UI.

## Cấu trúc mã nguồn

```text
src/main/java/com/ecommerce/aicommercesupport/
├── auth/       # JWT, refresh session và API xác thực
├── order/      # Order, order item và cache order
├── payment/    # Payment
├── shipment/   # Shipment
├── ticket/     # Ticket, hội thoại và workflow hỗ trợ
├── user/       # User và role
└── common/     # Security helper, cache eviction và xử lý lỗi dùng chung
```

Liquibase quản lý schema trong `src/main/resources/db/changelog/`. Test nằm dưới `src/test/java/` theo cùng cấu trúc module.