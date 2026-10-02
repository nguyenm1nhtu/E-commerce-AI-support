# Docker Compose

Các lệnh chạy backend theo Spring profile, test và build nằm trong
[hướng dẫn terminal](terminal-commands.md).

Hai file Compose độc lập chạy PostgreSQL có sẵn pgvector và Redis:

| Môi trường | Compose | Biến môi trường | Project |
| --- | --- | --- | --- |
| Dev | `compose.dev.yaml` | `.env.dev` | `ai-commerce-support-dev` |
| Production | `compose.prod.yaml` | `.env.prod` | `ai-commerce-support-prod` |

`.env.example` là mẫu được commit. `.env.dev`, `.env.prod` và các file `.env.*`
khác được Git bỏ qua. Khi clone mới, sao chép mẫu rồi điền cấu hình:

```powershell
Copy-Item .env.example .env.dev
Copy-Item .env.example .env.prod
```

Chỉ sao chép khi file đích chưa tồn tại để tránh ghi đè cấu hình đang dùng.
Dev cần `POSTGRES_PASSWORD`; prod cần cả `POSTGRES_PASSWORD` và
`REDIS_PASSWORD`, với mật khẩu riêng cho production. Mẫu để trống mật khẩu;
Compose sẽ báo lỗi nếu thiếu giá trị bắt buộc. Dùng dấu nháy đơn quanh mật khẩu
chứa `$` hoặc `#` để giữ nguyên giá trị.

## Dev

```powershell
docker compose --env-file .env.dev -f compose.dev.yaml config --quiet
docker compose --env-file .env.dev -f compose.dev.yaml up -d
docker compose --env-file .env.dev -f compose.dev.yaml down
```

PostgreSQL lắng nghe tại `localhost:5432`, Redis tại `localhost:6379`;
có thể đổi cổng trong `.env.dev`. Redis dev không yêu cầu mật khẩu.
Backend chạy từ IDE/Maven và cần cấu hình kết nối tương ứng.
`--env-file` cấp biến cho Compose, không tự cấu hình tiến trình Java chạy ngoài Docker.

## Production

```powershell
docker compose --env-file .env.prod -f compose.prod.yaml config --quiet
docker compose --env-file .env.prod -f compose.prod.yaml up -d
docker compose --env-file .env.prod -f compose.prod.yaml down
```

Production không publish cổng database/cache ra host. Backend khi được triển khai
cần tham gia mạng `ai-commerce-support-prod_default`, kết nối `postgres:5432`
và `redis:6379`, đồng thời cung cấp thông tin xác thực tương ứng.
File này chỉ cấu hình hai dịch vụ hạ tầng; chưa triển khai backend, CI/CD hay monitoring.

## Dữ liệu

Mỗi project có network và named volume riêng; không chạy ghép hai file bằng nhiều
tham số `-f`. Không dùng cùng giá trị `-p` hoặc `COMPOSE_PROJECT_NAME` cho dev và
prod vì chúng sẽ ghi đè tên project và làm mất sự tách biệt này.
`down` giữ dữ liệu, còn `down -v` xóa các volume của môi trường được chọn.

Nếu từng chạy `compose.yaml` cũ, volume mang tiền tố `ai-commerce-support_`
vẫn được giữ lại nhưng không tự gắn vào project dev/prod mới. Cần backup/restore
nếu muốn chuyển dữ liệu cũ; thay đổi này không xóa container hay volume hiện có.
Đổi mật khẩu PostgreSQL trong env không tự đổi mật khẩu trong database đã khởi tạo.
Image pgvector cung cấp extension; Liquibase bật `vector` qua changeset đầu tiên
khi backend khởi động. Database production cần hỗ trợ pgvector và tài khoản chạy
migration cần quyền tạo extension.

Cách dùng `--env-file` và kiểm tra biến bắt buộc theo
[tài liệu Docker Compose](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).
