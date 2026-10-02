# Lệnh chạy theo profile

Chạy tại thư mục chứa `pom.xml`, bằng PowerShell với JDK 25.

## Dev

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

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
