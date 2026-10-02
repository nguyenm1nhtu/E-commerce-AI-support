# Lệnh chạy theo profile

Chạy tại thư mục chứa `pom.xml`, bằng PowerShell với JDK 25.

## Dev

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
Các lệnh trên chỉ chọn Spring profile, không tự nạp `.env.dev`/`.env.prod`.
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
   `src/main/resources/db/changelog/`. Hiện dự án chưa có entity nên lệnh có thể
   báo không có thay đổi và không tạo file.

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
