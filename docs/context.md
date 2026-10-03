# AI-Powered E-commerce Customer Support Platform

> Bản tóm tắt thiết kế dành cho coding agents, dựa duy nhất trên `AI_Ecommerce_Support_Project_Design.pdf`. Giữ nguyên phạm vi, tên kỹ thuật, ràng buộc và mức độ bắt buộc/tùy chọn của tài liệu; các con số trong ví dụ không được hiểu thành quy tắc chung ngoài ngữ cảnh đó. Đây là bản thiết kế, không phải báo cáo tính năng đã triển khai. Số mục bên dưới tương ứng 20 mục của PDF để đối chiếu.

Các quyết định bổ sung trong quá trình phát triển được ghi rõ là ghi chú triển
khai, tách biệt với nội dung thiết kế gốc trong PDF.

## 1. Tổng quan và định vị

- **Tên tạm:** E-commerce Support AI; tên có thể thay đổi, kiến trúc và phạm vi mới là đầu ra chính.
- **Thời gian mục tiêu:** 8 tuần / khoảng 2 tháng, quy mô portfolio cho một người triển khai.
- **Vai trò thể hiện:** Java Backend Engineer with Applied AI / Agent Systems; xây dựng hệ thống tích hợp AI an toàn, quan sát được và triển khai được.
- **Sản phẩm:** backend hỗ trợ khách hàng thương mại điện tử theo phong cách production, quản lý dữ liệu đơn hàng, thanh toán, vận chuyển, ticket, yêu cầu trả hàng và kiến thức hỗ trợ. AI tham gia luồng nghiệp vụ thực tế.
- **Stack chính:** Java 21, Spring Boot, PostgreSQL + pgvector, Redis, Spring AI, Docker.
- **Cách tổ chức:** modular monolith; CI/CD, observability và auditability theo hướng production-like.
- **Phạm vi AI:** RAG, tool-calling agent, multi-model routing và tự động hóa quá trình phát triển.
- Tài liệu đồng thời là blueprint triển khai, nội dung trình bày portfolio và hướng dẫn thảo luận khi phỏng vấn.

Các nguyên tắc xuyên suốt:

1. Backend là nguồn sự thật về danh tính, phân quyền, giao dịch và quy tắc nghiệp vụ.
2. RAG dành cho kiến thức ít thay đổi: chính sách trả hàng, hoàn tiền, vận chuyển, bảo hành và playbook hỗ trợ.
3. Agent truy cập dữ liệu giao dịch hiện tại và thực hiện hành động qua các tool được kiểm soát.
4. Java sở hữu validation, authorization, execution và persistence; AI đề xuất/điều phối, backend quyết định điều gì được phép.
5. Multi-model phân công model theo tác vụ; multi-agent chỉ là mở rộng sau khi một orchestrator cùng các tool đã ổn định.
6. Development AI hỗ trợ README, PR summary, changelog và issue triage qua GitHub Actions.

## 2. Bài toán và phạm vi

### 2.1. Bài toán

Câu hỏi hỗ trợ có thể cần cả dữ liệu vận hành hiện tại và chính sách: “Đơn đã giao 10 ngày trước; tôi có được trả hàng không, và nếu được thì tạo yêu cầu.” Hệ thống phải kết hợp sự thật về đơn hàng với kiến thức chính sách, chỉ thực thi hành động qua application services có kiểm soát.

### 2.2. Trong phạm vi

| Mảng | Chức năng |
| --- | --- |
| Identity | Đăng ký/đăng nhập, JWT, role `CUSTOMER` và `SUPPORT_AGENT`, kiểm tra quyền sở hữu |
| Commerce support | Order read model, trạng thái thanh toán/vận chuyển, yêu cầu trả hàng |
| Ticketing | Tạo ticket, tin nhắn, chuyển trạng thái, xử lý có AI hỗ trợ, chuyển cho nhân viên |
| Knowledge base | Ingestion, chunking, embedding, metadata/versioning, retrieval, trích dẫn |
| Agent | Intent routing, tool calling, hành động có giới hạn, audit trail, fallback/handoff |
| Dev automation | README progress, PR summary, CHANGELOG, issue triage |
| Engineering | Testing, Docker, CI/CD, observability, deployment, kiểm soát bảo mật |

### 2.3. Ngoài phạm vi trong 2 tháng

- Marketplace đầy đủ: seller onboarding, catalog search, checkout và xử lý thanh toán thật.
- Kubernetes, event sourcing, CQRS và chia tách thành nhiều microservice lớn.
- Tự động sinh code/merge không có human review.
- Huấn luyện model hoặc triển khai transformer.
- Multi-agent swarm quy mô lớn.

## 3. Vai trò và vòng đời ticket

| Vai trò | Hành động chính | Tương tác với AI |
| --- | --- | --- |
| Customer | Xem trạng thái đơn, tạo ticket, yêu cầu trả hàng | Hỏi hỗ trợ; cho phép/xác nhận hành động nhạy cảm |
| Support Agent | Xem escalation, giải quyết ticket, override/phê duyệt hành động | Xem bằng chứng, nguồn, tool trace và câu trả lời đề xuất |
| Admin / Knowledge Manager | Upload, cập nhật, vô hiệu hóa chính sách | Quản lý corpus đang hoạt động và các phiên bản |
| Developer / Maintainer | Build, test, deploy, monitor | Dùng workflow AI cho tài liệu và triage |

Vòng đời ticket trong nguồn:

- `OPEN → AI_HANDLING → WAITING_CUSTOMER → RESOLVED`.
- Nhánh chuyển cho người xử lý: `AI_HANDLING → ESCALATED → HUMAN_HANDLING → RESOLVED`.

**Human-in-the-loop:** retrieval độ tin cậy thấp, thiếu dữ liệu bắt buộc, xung đột chính sách, tool lỗi hoặc hành động tác động lớn phải dẫn tới escalation thay vì tạo ra sự chắc chắn không có căn cứ.

## 4. Kiến trúc tổng thể

Khuyến nghị **modular monolith** để giữ độ thực tế của backend nhưng vẫn dễ deploy/debug trong dự án solo 2 tháng. AI là module trong Spring Boot; không tách Python microservice trừ khi yêu cầu tương lai có lý do phù hợp.

| Thành phần | Trách nhiệm | Công nghệ |
| --- | --- | --- |
| Web UI | Màn hình customer/support/admin | React + TypeScript |
| Backend API | Nghiệp vụ, auth, validation, transaction | Java 21 + Spring Boot |
| Persistence | Dữ liệu giao dịch | PostgreSQL + Spring Data JPA |
| Vector store | Embedding và retrieval theo metadata | pgvector |
| Cache / rate control | Cache, trạng thái tạm thời giới hạn | Redis |
| AI integration | Chat, embedding, RAG, tool calling | Spring AI |
| Observability | Metrics, health, telemetry ứng dụng/AI | Actuator + Micrometer + Prometheus/Grafana |
| CI/CD | Build, test, container, deploy, Dev AI | GitHub Actions |
| Runtime | Chạy container | Docker + AWS ECS/EC2; RDS PostgreSQL |

Quan hệ trong sơ đồ nguồn: UI gọi Spring Boot qua HTTPS; ứng dụng gọi LLM providers qua Model API, PostgreSQL qua JPA, pgvector qua VectorStore và Redis cho cache/rate limit. GitHub Actions build/test ứng dụng và deploy lên AWS ECS/EC2 + RDS.

## 5. Luồng nghiệp vụ chính

### 5.1. Runtime hỗ trợ khách hàng

1. Customer gửi tin nhắn trong ticket/chat.
2. Router phân loại yêu cầu: thông tin, dữ liệu giao dịch hoặc hành động.
3. Agent gọi backend tools lấy sự thật hiện tại về order/payment/shipment.
4. RAG chỉ truy xuất policy chunks liên quan và đang active.
5. Agent tổng hợp bằng chứng, kiểm tra điều kiện hành động rồi trả lời, thực hiện hành động được phép, hỏi xác nhận hoặc escalation.
6. Ghi từng tool call và kết quả vào audit trail.

Guardrails của luồng gồm RBAC, validation, approval, audit và handoff.

### 5.2. Ví dụ trả hàng trong tài liệu

Khách hỏi: “I received ORD-101 ten days ago. Can I return the headphones?”

1. `getOrder("ORD-101")`.
2. `getShipmentStatus("ORD-101")` trả `DELIVERED`, `deliveredAt` cách đây 10 ngày.
3. RAG lấy Return Policy đang active, cho phép trả trong 14 ngày.
4. Kiểm tra quyền sở hữu item/order và điều kiện trả hàng.
5. Hỏi xác nhận hoặc gọi `createReturnRequest(...)` tùy action policy.
6. Lưu `AgentExecution`, `ToolExecution` và tham chiếu tài liệu nguồn.

### 5.3. Nguồn sự thật

| Loại câu hỏi | Nguồn | Ví dụ |
| --- | --- | --- |
| Trạng thái hiện tại | Database/tool | ORD-101 đã được hoàn tiền chưa? |
| Chính sách/kiến thức | RAG | Thời gian xử lý hoàn tiền là bao lâu? |
| Kết hợp | Tools + RAG | Đơn đã giao còn đủ điều kiện trả hàng không? |
| Hành động | Tool sau validation/authorization | Tạo yêu cầu trả hàng |

## 6. Data model và API MVP

### 6.1. Core entities

Danh sách dưới đây là các trường/quan hệ quan trọng nêu trong nguồn, kèm các
trường bổ sung được giải thích trong ghi chú triển khai bên dưới; không phải
schema đầy đủ.

| Entity | Trường / quan hệ |
| --- | --- |
| `User` | `id`, `email`, `passwordHash`, `role`; bổ sung `firstName`, `lastName` |
| `Order` | `id`, `userId`, `status`, `orderedAt`, `totalAmount` |
| `OrderItem` | `orderId`, `productName`, `quantity`, `unitPrice` |
| `Payment` | `orderId`, `status`, `providerRef`, `paidAt` |
| `Shipment` | `orderId`, `status`, `carrier`, `trackingCode`, `deliveredAt` |
| `Ticket` | `userId`, `category`, `status`, `priority`, `assignedTo` |
| `TicketMessage` | `ticketId`, `senderType`, `content`, `createdAt` |
| `ReturnRequest` | `orderId`, `itemId`, `reason`, `status`, `createdAt` |
| `KnowledgeDocument` | `name`, `type`, `version`, `effectiveDate`, `status`, `checksum` |
| `DocumentChunk` | `documentId`, `text`, `embedding`, `metadata` |
| `AgentExecution` | `ticketId`, `model`, `status`, `latency`, `result` |
| `ToolExecution` | `agentExecutionId`, `toolName`, `input`, `output`, `duration`, `success` |

Ghi chú triển khai bổ sung (không phải ràng buộc trong PDF): `Shipment.carrier`
dùng enum `Carrier` gồm `GHTK`, `GHN`, `VIETTEL_POST` (Viettel Post).
Xem [commerce model](commerce-model.md) để biết mapping và ràng buộc database.

Ghi chú triển khai bổ sung cho User và xác thực (theo yêu cầu ngày 2026-10-03,
không phải ràng buộc trong PDF):

- `User.firstName` là tên, có thể gồm tên đệm; `User.lastName` là họ. Mỗi trường
  tối đa 100 ký tự, không được để trống khi tạo User qua JPA.
- DTO xác thực nằm trong `auth/dto`. `RegisterRequest` yêu cầu `email`,
  `password`, `firstName`, `lastName`; không nhận `role` từ người đăng ký.
  Email phải đúng định dạng, tối đa 254 ký tự; mật khẩu đăng ký tối thiểu 8 ký tự.
- `LoginRequest` vẫn chỉ nhận `email` và `password`, không dùng tên để đăng nhập.
- UI sau này dùng tên để hiển thị thay cho email. Ví dụ `firstName = "Minh Tú"`,
  `lastName = "Nguyễn"` có thể hiển thị thành `Nguyễn Minh Tú` theo thứ tự họ tên
  tiếng Việt. Phần hiển thị UI chưa được triển khai.
- Migration `005-add-user-names.sql` thêm `first_name` và `last_name` vào bảng
  `users`. Các tài khoản cũ giữ giá trị `NULL` để bổ sung tên thật sau; không lấy
  email làm tên và không tự gán tên giả. Database cho phép `NULL` để tương thích
  dữ liệu cũ, còn DTO đăng ký và validation của entity bắt buộc đủ hai trường.
- Hiện đã có entity, repository, migration và DTO; luồng API đăng ký/đăng nhập
  và xác thực JWT chưa được triển khai. Danh sách API bên dưới vẫn là thiết kế.

### 6.2. API inventory

| Method | Endpoint | Mục đích |
| --- | --- | --- |
| POST | `/api/auth/register` | Đăng ký |
| POST | `/api/auth/login` | Đăng nhập JWT |
| GET | `/api/orders` | Danh sách đơn của customer |
| GET | `/api/orders/{id}` | Chi tiết đơn |
| GET | `/api/orders/{id}/payment` | Trạng thái thanh toán |
| GET | `/api/orders/{id}/shipment` | Trạng thái vận chuyển |
| POST | `/api/tickets` | Tạo ticket |
| GET | `/api/tickets/{id}` | Chi tiết ticket |
| POST | `/api/tickets/{id}/messages` | Thêm tin nhắn / kích hoạt AI hỗ trợ |
| POST | `/api/orders/{id}/returns` | Tạo yêu cầu trả hàng |
| POST | `/api/admin/knowledge/documents` | Ingest tài liệu chính sách |
| PATCH | `/api/admin/knowledge/documents/{id}/status` | Activate/deactivate phiên bản kiến thức |
| POST | `/api/ai/knowledge/query` | Kiểm thử RAG trực tiếp |
| POST | `/api/ai/agent/chat` | Tương tác agent |

## 7. Kiến trúc RAG

### 7.1. Knowledge corpus

Bắt đầu bằng Markdown để versioning xác định và dễ review; thêm PDF upload sau khi retrieval pipeline hoạt động. Khoảng **10-20 tài liệu** đủ cho portfolio nếu có versioning thực tế và chính sách lịch sử xung đột.

```text
knowledge-base/
  return-policy.md
  refund-policy.md
  shipping-policy.md
  warranty-policy.md
  cancellation-policy.md
  payment-faq.md
  damaged-item-policy.md
  voucher-policy.md
  support-handbook.md
```

### 7.2. Pipeline

Ingestion: `Document → parse → normalize → split/chunk → attach metadata → embed → store in pgvector`.

| Bước | Cách thực hiện / mục đích |
| --- | --- |
| Parse | Markdown trước, PDF parser sau để dễ kiểm thử giai đoạn đầu |
| Chunk | Bắt đầu đơn giản; điều chỉnh size/overlap bằng thực nghiệm, cân bằng tính liền mạch ngữ nghĩa và độ chi tiết retrieval |
| Metadata | `type`, `version`, `effectiveDate`, `status`, `source`; lọc nội dung cũ hoặc sai domain |
| Embedding | Model embedding chuyên dụng cho biểu diễn tìm kiếm ngữ nghĩa |
| Store | PostgreSQL + pgvector, giữ vector gần nền tảng dữ liệu quan hệ |
| Retrieve | Top-K + metadata filter lấy policy chunks liên quan đang active |
| Generate | LLM trả lời dựa trên bằng chứng đã lấy |
| Cite | Trả tên tài liệu/section để kiểm tra được nguồn |

### 7.3. Đánh giá

- Tạo **30-50 câu hỏi benchmark**, mỗi câu có tài liệu nguồn kỳ vọng.
- Đo retrieval hit rate / độ đúng nguồn top-K trước khi đánh giá văn phong sinh ra.
- Theo dõi unsupported-answer rate và escalation rate.
- Kiểm tra version filtering với policy trả hàng **7 ngày đã obsolete** và policy **14 ngày đang active**.

## 8. Kiến trúc agent và audit

Agent là vòng lặp do ứng dụng kiểm soát. Model có thể yêu cầu tool call nhưng Java thực hiện kiểm tra, phân quyền, thực thi và lưu kết quả. **Business invariants nằm trong Java services, không nằm trong prompt.**

### 8.1. Tool catalogue

| Tool | Loại | Mục đích | Guard |
| --- | --- | --- | --- |
| `getOrder` | Read | Lấy order và thông tin quyền sở hữu | Customer chỉ đọc đơn của mình |
| `getPaymentStatus` | Read | Trạng thái thanh toán hiện tại | Kiểm tra quyền sở hữu đơn |
| `getShipmentStatus` | Read | Trạng thái vận chuyển hiện tại | Kiểm tra quyền sở hữu đơn |
| `searchPolicy` | Read | Lấy knowledge chunks | Filter chỉ tài liệu active |
| `checkReturnEligibility` | Read/compute | Quy tắc nghiệp vụ xác định + sự thật chính sách | Không chỉ dựa vào suy luận free-text của model |
| `createReturnRequest` | Write | Lưu yêu cầu trả hàng | Validation + idempotency + xác nhận người dùng khi cần |
| `escalateTicket` | Write | Chuyển vào hàng đợi nhân viên | Luôn cho phép khi đạt ngưỡng bất định |

### 8.2. Execution record chi tiết

Mục 8.2 của nguồn mô tả chi tiết bản ghi audit bên cạnh bảng entity khái quát ở mục 6:

| Record | Trường |
| --- | --- |
| `AgentExecution` | `id`, `ticketId`, `model`, `promptVersion`, `status`, `startedAt` / `completedAt`, `inputTokens` / `outputTokens` (tùy chọn), `finalOutcome` |
| `ToolExecution` | `agentExecutionId`, `toolName`, `sanitizedInput`, `sanitizedOutput`, `durationMs`, `success` / `errorCode` |

## 9. Multi-model và multi-agent

### 9.1. Multi-model routing: khuyến nghị

| Vai trò model | Tác vụ | Lý do |
| --- | --- | --- |
| Chat model nhanh/chi phí thấp | Phân loại intent, tạo tiêu đề ticket, tóm tắt ngắn | Tác vụ thường xuyên, rủi ro thấp |
| Model có năng lực reasoning | Điều phối hỗ trợ phức tạp, chọn tool | Dành chi phí/chất lượng cao cho nơi cần |
| Embedding model | Embedding tài liệu và truy vấn RAG | Biểu diễn chuyên dụng cho retrieval |
| Local model qua Ollama, tùy chọn | Thử nghiệm phát triển local/fallback | Giảm phụ thuộc API cho tác vụ không trọng yếu |

### 9.2. Multi-agent: mở rộng tùy chọn, không thuộc MVP

Khởi đầu bằng **một orchestrator với typed tools**. Chỉ tách trách nhiệm khi codebase khó quản lý, vẫn giữ orchestrator cấp trên và lớp audit/guardrail dùng chung:

| Agent chuyên biệt | Trách nhiệm nếu mở rộng |
| --- | --- |
| Knowledge Agent | Chỉ RAG |
| Order Support Agent | Read tools cho order/payment/shipment |
| Return Agent | Eligibility + return workflow |
| Handoff Agent | Gói thông tin escalation cho human support |

Khuyến nghị trong 2 tháng: triển khai multi-model; chỉ làm multi-agent sau khi đường đi single-agent end-to-end, kiểm thử và deployment đã hoàn tất.

## 10. Development AI và GitHub automation

Developer push/PR/issue kích hoạt GitHub Actions; Dev AI model tóm tắt/phân loại để tạo các đầu ra sau:

| Automation | Trigger | Đầu vào AI | Đầu ra / hành động |
| --- | --- | --- | --- |
| README progress updater | Merge/push vào `main` | Commit/PR metadata + progress markers | Chỉ viết lại vùng progress được đánh dấu |
| PR summary generator | PR opened/synchronized | Git diff + PR metadata | Comment gồm summary, impact, tests |
| CHANGELOG generator | Release / merge | Tiêu đề các PR đã merge + labels | Cập nhật release notes/changelog |
| Issue triage | Issue opened | Tiêu đề/nội dung issue | Gắn type/module labels; có thể gợi ý priority |

Vùng README do AI quản lý:

```markdown
## Development Progress
<!-- AI_PROGRESS_START -->
... AI-managed section only ...
<!-- AI_PROGRESS_END -->
```

Giới hạn này ngăn model viết lại giải thích kiến trúc, hướng dẫn setup và tài liệu được biên soạn thủ công. Ranh giới an toàn: quyền `GITHUB_TOKEN` tối thiểu, chỉ sửa README trong markers và human review với thay đổi code.

Không tự động hóa ở giai đoạn đầu:

- Tự động phê duyệt merge.
- Tự động deploy production chỉ dựa trên phán đoán model.
- Âm thầm viết lại code trên `main`.
- Thay cấu hình nhạy cảm bảo mật không qua human review.

## 11. Công cụ theo giai đoạn

| Giai đoạn | Công cụ | Cách dùng |
| --- | --- | --- |
| Bootstrap | Spring Initializr, Maven | Tạo project Java và dependency graph |
| API/nghiệp vụ | Spring Boot, Spring Web | Controller, service, DTO, validation |
| Security | Spring Security + JWT | Authentication, RBAC, ownership |
| Persistence | Spring Data JPA + PostgreSQL | Orders, tickets, returns, audit |
| Schema evolution | Liquibase + Hibernate `validate` | Liquibase quản lý migration có version control; Hibernate kiểm tra schema khớp với entity |
| Caching | Redis | Hot reads, hỗ trợ rate limit, conversation state tùy chọn |
| RAG storage | pgvector | Lưu/truy vấn vector embedding trong PostgreSQL |
| AI framework | Spring AI | ChatClient, VectorStore, RAG, tool calling, model abstractions |
| Local AI tùy chọn | Ollama | Thử nghiệm local development/fallback |
| Testing | JUnit 5, Mockito, Testcontainers | Unit/integration tests với container Postgres/Redis thực |
| API docs | OpenAPI/Swagger | Tài liệu contract có thể tương tác |
| Local orchestration | Docker Compose | Backend + Postgres/pgvector + Redis + observability |
| CI/CD | GitHub Actions | Build, test, image, Dev AI workflows, deployment |
| Container registry | Amazon ECR hoặc Docker Hub | Lưu image có phiên bản |
| Cloud runtime | AWS ECS Fargate hoặc EC2 | Chạy backend container |
| Managed DB | Amazon RDS PostgreSQL | DB staging/production; phải xác minh hỗ trợ pgvector ở engine/version được chọn |
| Metrics | Spring Boot Actuator + Micrometer | Health/JVM/HTTP/custom AI metrics |
| Dashboards | Prometheus + Grafana | Dashboard observability local/portfolio |
| Secrets | GitHub Secrets + cơ chế secret AWS | API keys và deployment credentials; không để secrets trong repo |

## 12. Security và guardrails

### 12.1. Application security

- JWT authentication và role-based authorization.
- Object ownership checks cho order, ticket, return.
- Validate mọi tool argument và external request.
- Idempotency cho write tools như tạo yêu cầu trả hàng.
- Log prompt/tool phải che credentials, tokens và dữ liệu khách hàng nhạy cảm.
- Chỉ admin được ingest knowledge và đổi trạng thái tài liệu.

### 12.2. Kiểm soát AI

| Rủi ro | Biện pháp |
| --- | --- |
| Bịa chính sách | RAG citations + nhánh insufficient evidence + active metadata filter |
| Hành động trái quyền | Kiểm tra Spring Security/service ở tool; model không vượt được backend |
| Prompt injection trong tài liệu | Xem retrieved content là dữ liệu; giới hạn tool; không lộ secrets hoặc cung cấp shell tùy ý |
| Tool loop lặp lại | Giới hạn số vòng gọi tool + timeout + circuit/fallback |
| Write action sai | Xác nhận/phê duyệt rõ ràng cho thao tác có tác động cao hơn |
| Policy obsolete | Metadata version/effectiveDate/status + admin lifecycle |

### 12.3. GitHub Actions security

- Đặt quyền `GITHUB_TOKEN` tối thiểu cho từng workflow.
- Model/API keys nằm trong Actions secrets, không nằm trong YAML/repository.
- Ưu tiên xác thực cloud ngắn hạn, ví dụ OIDC, khi khả thi.
- Pin third-party actions hoặc giảm dependency với workflow nhạy cảm bảo mật.

## 13. Observability, reliability và evaluation

### 13.1. Metrics

| Metric | Mục đích |
| --- | --- |
| HTTP p50/p95 latency, error rate | Độ tin cậy backend |
| DB connection pool, query latency | Phát hiện bottleneck persistence |
| Cache hit ratio | Đánh giá ích lợi Redis |
| `ticket.ai.auto_resolved.count` | Giá trị nghiệp vụ của AI |
| `agent.tool.failure.rate` | Regression tool/reliability |
| `agent.handoff.rate` | Tín hiệu chất lượng/an toàn |
| `rag.retrieval.hit.rate` | Chất lượng retrieval trên benchmark |
| `llm.latency` / token usage | Nhận biết chi phí/hiệu năng |

### 13.2. Xử lý lỗi

| Sự cố | Fallback |
| --- | --- |
| LLM timeout | Retry trong budget; sau đó escalation hoặc ticket path không dùng AI |
| Embedding provider không khả dụng | Tạm dừng ingestion; retrieval vector hiện có vẫn dùng được nếu có query embeddings |
| RAG relevance thấp | Không bịa; hỏi làm rõ hoặc escalation |
| Write tool lỗi | Rollback transaction, lỗi truy vết được, tránh tạo request trùng |
| Redis không khả dụng | Fallback DB cho các đường cache không thiết yếu |
| GitHub Dev AI lỗi | Workflow báo lỗi, CI/build bình thường vẫn độc lập |

### 13.3. Ví dụ SLO portfolio trong nguồn

Đối với demo/staging: backend health khả dụng trong thời gian demo; retrieval hit **>95%** với nguồn kỳ vọng trong **top-3** trên benchmark; **0** write-tool execution trái quyền trong test; mọi write tool tạo audit record. Đây là ví dụ mục tiêu trong thiết kế, không phải kết quả đã đo.

## 14. CI/CD và deployment

### 14.1. Pipeline khuyến nghị

1. **Pull request:** compile, unit test, integration test, static analysis tùy chọn, PR AI summary.
2. **Merge vào `main`:** chạy lại gates, build Docker image, tag bằng commit SHA, push registry, cập nhật README progress và changelog.
3. **Deployment:** xác thực AWS, deploy image, smoke/health check, theo dõi metrics.

Sơ đồ nguồn thể hiện Maven build, unit/integration tests, Docker image, registry ECR/Docker Hub, AWS deploy, smoke test/metrics. Các gates trong sơ đồ: `formatting/lint → tests → image scan → deploy approval`.

### 14.2. Chọn môi trường

| Phương án | Ưu điểm | Đánh đổi |
| --- | --- | --- |
| EC2 + Docker Compose | Dễ hình dung vận hành, SSH/debug thuận tiện | Tự quản lý server nhiều hơn |
| ECS Fargate | Container scheduling được quản lý; thể hiện kinh nghiệm AWS | Nhiều khái niệm AWS/IAM/networking hơn |
| Render/Railway, fallback | Deploy nhanh nếu AWS cản tiến độ | Ít chiều sâu kiến trúc cloud |

ECS Fargate phù hợp nếu muốn kinh nghiệm AWS theo hướng container; EC2 + Compose đơn giản hơn về vận hành. Chọn **một** phương án và ghi trade-off trong ADR; tránh triển khai cả hai trừ khi còn thời gian.

## 15. Cấu trúc repository và tài liệu

```text
ai-commerce-support/
  backend/
    src/main/java/com/example/support/
      auth/ user/ order/ payment/ shipment/ ticket/ returnrequest/
      ai/rag/ ai/agent/ ai/tool/
      common/config/ common/security/ common/exception/
    src/test/...
  frontend/
  knowledge-base/
  docs/
    architecture.md
    api.md
    rag-design.md
    agent-design.md
    security.md
    runbook.md
    adr/
      001-modular-monolith.md
      002-pgvector.md
      003-spring-ai.md
      004-deployment-target.md
  .github/workflows/
    ci.yml
    deploy.yml
    ai-pr-summary.yml
    ai-readme-progress.yml
    ai-changelog.yml
    ai-issue-triage.yml
  docker-compose.yml
  README.md
```

| Tài liệu | Nội dung |
| --- | --- |
| README | Tổng quan cho nhà tuyển dụng, hình kiến trúc, quick start, demo, tiến độ |
| Architecture | Ranh giới component, data flow, trade-off |
| ADR | Lý do quyết định, phương án thay thế, hệ quả |
| RAG design | Corpus, chunking, metadata, retrieval evaluation |
| Agent design | Tools, authorization, handoff, audit |
| Security | Trust boundaries, secrets, threat model, AI risks |
| Runbook | Deploy, health check, lỗi thường gặp, rollback |

## 16. Kiến thức tối thiểu

| Chủ đề | Mức cần biết trước/trong khi làm |
| --- | --- |
| Java | OOP, records/DTOs, collections, exceptions, generics, streams, concurrency cơ bản |
| Spring Boot | Controller/service/repository, DI, configuration, validation, exception handling |
| Spring Security | Authentication/authorization, JWT, roles, kiểm tra method/resource |
| JPA/Hibernate | Relations, transactions, lazy/eager, nhận biết N+1, pagination, migrations |
| SQL/PostgreSQL | Joins, indexes, constraints, transactions, query plan cơ bản |
| Docker | Images, containers, volumes, networks, Compose, environment variables |
| Testing | JUnit, Mockito, integration tests, Testcontainers cơ bản |
| HTTP/API | REST semantics, status codes, idempotency, pagination, OpenAPI |
| LLM | Tokens, context window, system/user messages, structured output, hallucination |
| Embeddings/RAG | Embedding, chunking, vector similarity, top-K, metadata filters, evaluation |
| Agent systems | Tool calling, orchestration loop, typed inputs, permission boundary, retry/handoff |
| CI/CD | GitHub Actions triggers, secrets, permissions, build/deploy stages |
| Cloud | AWS IAM, networking, container runtime, managed DB cơ bản |
| Observability | Logs, metrics, khái niệm tracing, health checks, chẩn đoán lỗi |

Không cần làm điều kiện tiên quyết: toán transformer, model training, PyTorch/TensorFlow, Kubernetes, distributed consensus hoặc lý thuyết ML chuyên sâu.

## 17. Lộ trình 8 tuần

| Tuần | Đầu ra chính | Điều kiện hoàn thành |
| --- | --- | --- |
| 1 | Bootstrap + vertical slice Order/Payment/Shipment | Docker Postgres chạy; GET order detail hoạt động; migration + tests |
| 2 | Auth + Ticketing + Return workflow | JWT/RBAC; ticket messages; return service; error handling |
| 3 | Hardening backend | Pagination, Redis, ownership checks, Testcontainers, OpenAPI |
| 4 | Docker + nền tảng observability | Full Compose; Actuator/Micrometer; CI build/test; deploy skeleton |
| 5 | Knowledge ingestion + pgvector | Docs → chunks → embeddings; metadata/versioning; retrieval endpoint |
| 6 | Chất lượng RAG | Citations, active-policy filtering, benchmark 30-50 câu, retrieval evaluation |
| 7 | Tool-calling agent + audit | Read/write tools, agent flow, AgentExecution/ToolExecution, handoff |
| 8 | Dev AI + deploy + hoàn thiện | README/PR/changelog/issue automation, cloud deploy, demo script, tài liệu cuối |

**Stretch goals chỉ sau khi MVP đã deploy:** multi-agent specialization; streaming responses; provider fallback bổ sung/local Ollama; ảnh Grafana dashboard trong README; cung cấp/sử dụng MCP nếu trực tiếp cải thiện phần trình bày kiến trúc.

## 18. Definition of Done và demo

- [ ] Clone repository và chạy local được bằng lệnh trong tài liệu.
- [ ] Database migrations tạo schema một cách xác định.
- [ ] Core APIs được bảo vệ và có integration tests.
- [ ] RAG trích dẫn nguồn policy active và loại bỏ/tránh policy obsolete trong benchmark tests.
- [ ] Agent dùng live tools, không thực hiện được write action trái quyền.
- [ ] Agent execution và tool execution có thể audit.
- [ ] CI chạy trên pull requests và `main`.
- [ ] Ít nhất **2** development AI automations hoạt động; mục tiêu **4**.
- [ ] Có môi trường đã deploy hoặc quy trình deployment tái lập được.
- [ ] README chứa kiến trúc, demo flow, trade-offs và trạng thái dự án.

Checklist trên là tiêu chí thiết kế; chưa xác nhận mục nào đã hoàn thành.

## 19. Nguyên tắc tài liệu theo phong cách engineering nội bộ

- Làm rõ thuộc tính chất lượng kiến trúc: security, reliability, operations, cost trade-offs.
- Dùng ADR ghi lại quyết định modular monolith, pgvector, AI framework và deployment.
- Ghi rõ operational readiness: health checks, metrics, failure modes, runbook, rollback.
- Thể hiện least privilege, workflow permissions và secret handling.
- Đặt AI accountability thành phần chính: citations, audit trail, tool guards, low-confidence handoff, evaluation.
- Phân biệt sự thật giao dịch từ DB/tools và kiến thức chính sách từ RAG.
- Tăng độ phức tạp từng bước: single orchestrator trước multi-agent; modular monolith trước microservices.

**Docs as code:** mọi thay đổi đáng kể về kiến trúc/workflow nên cập nhật Markdown/ADR liên quan trong cùng PR. AI có thể hỗ trợ tóm tắt, con người vẫn chịu trách nhiệm về tính đúng đắn của kiến trúc.

## 20. Tài liệu tham khảo của PDF

Các nguồn dưới đây được PDF ghi nhận là đã truy cập tháng 09/2026; được giữ lại để tra cứu, không phải xác nhận cập nhật mới trong bản tóm tắt này.

| Nguồn | URL | Nội dung tham chiếu |
| --- | --- | --- |
| Spring AI Reference | https://docs.spring.io/spring-ai/reference/ | Model abstraction, RAG, vector stores, tool calling, observability, evaluation |
| Spring AI Tool Calling | https://docs.spring.io/spring-ai/reference/api/tools.html | Ứng dụng sở hữu việc thực thi tool |
| pgvector | https://github.com/pgvector/pgvector | Vector similarity search trong PostgreSQL |
| GitHub Actions - Events that trigger workflows | https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows | Triggers push/PR/issue/schedule |
| GitHub Actions - Secure use reference | https://docs.github.com/en/actions/reference/security/secure-use | Least privilege, secrets, workflow hardening |
| AWS Well-Architected Framework | https://docs.aws.amazon.com/wellarchitected/latest/framework/ | Operations, security, reliability, performance, cost |
| Spring Boot Actuator / Metrics | https://docs.spring.io/spring-boot/reference/actuator/metrics.html | Micrometer metrics, Prometheus export |
