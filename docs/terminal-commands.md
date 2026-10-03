# Lệnh chạy theo profile

Chạy tại thư mục chứa `pom.xml`, bằng PowerShell với JDK 25.

## Dev

**Cách 1: dùng script tự nạp `.env.dev`**

```powershell
.\dev.ps1
```

Chuẩn bị lần đầu: sao chép `.env.example` thành `.env.dev` nếu chưa có, rồi điền
`POSTGRES_PASSWORD` đúng với database dev. Bật Docker Desktop/Docker Engine trước
khi chạy script; Docker Compose cần hỗ trợ `up --wait`.

Script nạp `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `SERVER_PORT` từ `.env.dev`,
rồi chạy `docker compose --env-file .env.dev -f compose.dev.yaml up -d --wait`.
Sau khi PostgreSQL và Redis healthy, script chạy Maven Wrapper với profile `dev`.
Nếu Docker Compose lỗi, script trả về mã lỗi và không chạy backend.
Giá trị trong file được ưu tiên hơn
biến môi trường hiện có; biến không có trong file giữ giá trị hiện có hoặc mặc
định của ứng dụng. Script khôi phục môi trường terminal khi Maven kết thúc và
trả về mã thoát của Maven.

Ctrl+C chỉ dừng backend; PostgreSQL và Redis tiếp tục chạy nền. Có thể chạy lại
`.\dev.ps1` khi container vẫn đang chạy; Compose dùng lại container nếu cấu hình
không đổi. Script không tự gọi `down`, kể cả khi backend lỗi. Để dừng Docker dev:

```powershell
docker compose --env-file .env.dev -f compose.dev.yaml down
```

Lệnh này giữ dữ liệu trong volume.

Mỗi biến viết trên một dòng `KEY=value`; hỗ trợ dòng trống, comment `#`,
giá trị bọc nháy đơn hoặc nháy kép và comment cuối dòng sau khoảng trắng.
Giá trị được giữ nguyên, không nội suy `$VAR`/`${VAR}` hay xử lý escape.
Dùng nháy đơn quanh mật khẩu chứa `$` hoặc `#`, như mẫu cấu hình Docker.

Nếu PowerShell chặn thực thi script, dùng:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\dev.ps1
```

**Cách 2: chạy Maven trực tiếp**

Đặt các biến môi trường cần thiết trong terminal trước khi chạy (bắt buộc có
`POSTGRES_PASSWORD`). Lệnh này không tự nạp `.env.dev`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Backend dev chạy tại `http://localhost:8080`.

DevTools tự khởi động lại backend sau khi code Java được biên dịch. Giữ lệnh trên chạy,
rồi chạy `.\mvnw.cmd compile` ở terminal khác, hoặc dùng **Build Project** trong IDE.

## Test

```powershell
.\mvnw.cmd test
```

Test hiện tại đã có `@ActiveProfiles("test")`, tự nạp `application-test.yml` và dùng H2.

Kiểm tra riêng script dev (dùng Docker và Maven giả lập, không cần database):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\tests\dev.Tests.ps1
```

## Prod

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Nếu chạy bản JAR đã build:

```powershell
java -jar target/ai-commerce-support-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Dev/prod cần database và Redis đang chạy, truy cập được từ backend, cùng các biến
môi trường tương ứng. Dev cần `POSTGRES_PASSWORD`; prod cần `POSTGRES_DB`,
`POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`.
Lệnh Maven trực tiếp chỉ chọn Spring profile, không tự nạp `.env.dev`/`.env.prod`;
script `dev.ps1` nạp `.env.dev` cho backend dev.
Xem [cấu hình Docker Compose](docker-compose.md) về địa chỉ kết nối từng môi trường.

Liquibase chạy changelog tại `src/main/resources/db/changelog/` khi backend khởi động.
Migration đầu tiên bật extension `vector` trên PostgreSQL. Hibernate dùng
`ddl-auto: validate`; mỗi thay đổi schema tiếp theo cần thêm changeset mới.
Liquibase lưu lịch sử trong `DATABASECHANGELOG`. Database dev cũ vẫn còn bảng
lịch sử của công cụ migration trước đây (0 migration); bảng này được giữ nguyên.
Nếu môi trường khác đã có migration thực tế, cần đối chiếu và baseline trước khi chuyển.

## Thứ tự lệnh migration (dev)

Cần PostgreSQL dev đang chạy và đã đặt `POSTGRES_HOST`, `POSTGRES_PORT`,
`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` trong terminal chạy Maven.

1. Sau khi sửa entity, xem chênh lệch giữa entity và database:

   ```powershell
   .\mvnw.cmd compile liquibase:diff
   ```

2. Sinh file changeset đề xuất từ chênh lệch đó:

   ```powershell
   .\mvnw.cmd compile liquibase:diff "-Dliquibase.diffChangeLogFile=target/entity-diff.yaml"
   ```

   Kiểm tra file trong `target/`, chỉnh lại rồi thêm changeset phù hợp vào
   `src/main/resources/db/changelog/changes/` rồi include từ master changelog.
   Nếu entity và database đã khớp, lệnh có thể báo không có thay đổi và không tạo file.

3. Đánh dấu trạng thái hiện tại trước khi áp dụng migration mới:

   ```powershell
   .\mvnw.cmd liquibase:tag "-Dliquibase.tag=after-users"
   ```

4. Xem trước SQL, rồi áp dụng migration:

   ```powershell
   .\mvnw.cmd liquibase:updateSQL
   .\mvnw.cmd liquibase:update
   ```

5. Xem lịch sử để chọn mốc rollback:

   ```powershell
   .\mvnw.cmd liquibase:history
   ```

6. Nếu cần rollback changeset gần nhất, xem SQL trước rồi thực hiện:

   ```powershell
   .\mvnw.cmd liquibase:rollbackSQL "-Dliquibase.rollbackCount=1"
   Get-Content target/liquibase/migrate.sql
   .\mvnw.cmd liquibase:rollback "-Dliquibase.rollbackCount=1"
   ```

7. Nếu cần rollback về tag đã đánh dấu, xem SQL trước rồi thực hiện:

   ```powershell
   .\mvnw.cmd liquibase:rollbackSQL "-Dliquibase.rollbackTag=after-users"
   Get-Content target/liquibase/migrate.sql
   .\mvnw.cmd liquibase:rollback "-Dliquibase.rollbackTag=after-users"
   ```

`liquibase:diff` chỉ báo cáo chênh lệch; thêm `diffChangeLogFile` mới sinh file.
Xem kỹ file sinh tự động trước khi áp dụng. Rollback chỉ hoàn tác được những gì
changeset hỗ trợ; dữ liệu bị xóa không tự khôi phục nếu không có câu lệnh rollback.
