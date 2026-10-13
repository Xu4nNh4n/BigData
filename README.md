# Phân tích dữ liệu siêu thị đa nguồn bằng Apache Drill

Ứng dụng gồm POS, đơn online COD/chuyển khoản thủ công, nhập kho theo lô/FEFO, hủy đơn, trả từng món và hoàn tiền. Các nghiệp vụ tạo dữ liệu đầu vào cho đề tài Big Data; phạm vi hiện tại sử dụng MySQL, MongoDB Atlas, Astra DB/Cassandra và Apache Drill.

| Thành phần | Vai trò thực tế |
|---|---|
| MySQL `supermarket_api` | Nguồn giao dịch chính: sản phẩm, đơn/chi tiết đơn, thanh toán, lô hàng, xuất/nhập kho và Outbox |
| MongoDB `supermarket_catalog.products_catalog` | Catalog đồng bộ theo SKU, thuộc tính sản phẩm linh hoạt; không quyết định tồn kho hay thanh toán |
| Astra/Cassandra `supermarket_analytics.business_events_by_day` | Sự kiện nghiệp vụ, chia partition theo ngày UTC |
| JSON → Parquet/Snappy | Bản xuất sự kiện theo ngày để Drill phân tích; Parquet được công bố qua manifest |
| Apache Drill 1.21.2 | Truy vấn MySQL/MongoDB trực tiếp và các file JSON/Parquet; Cassandra đi qua bản xuất, không kết nối trực tiếp |
| Spring Boot / React | Backend gửi SQL cố định qua Drill REST; frontend hiển thị biểu đồ và đối soát |

Luồng: thao tác bán hàng → giao dịch MySQL và Outbox → dispatcher đồng bộ catalog/sự kiện → xuất Astra ra JSON → chuyển Parquet → Drill → API → giao diện. Đồng bộ và bản xuất có độ trễ; bảng đối soát giúp phát hiện dữ liệu thiếu hoặc lệch tiền.

## Chạy dự án

Cần Java 21, MySQL, Node.js và Docker. Sao chép `.env.example` thành `.env` nếu chưa có và điền cấu hình; backend nạp file này qua `spring.config.import`. Giữ timezone backend nhất quán với dữ liệu MySQL đã ghi. Đường dẫn bundle Astra nên dùng dấu `/` trong `.env`. Không ghi thông tin tài khoản thực vào tài liệu.

1. Tạo database MySQL `supermarket_api`, cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Liquibase tạo/cập nhật bảng khi backend chạy; không import schema cũ đè lên migration. `SQL/seed-current-schema.sql` là dữ liệu nền, chạy sau migration khi cần.
2. Cấu hình MongoDB URI có database `supermarket_catalog`; Astra dùng application token, secure-connect bundle và keyspace `supermarket_analytics`. Script tham khảo trong `database/mongodb` và `database/cassandra`.
3. Chạy Drill rồi backend/frontend bằng các terminal riêng:

```powershell
docker compose up -d --build drill
.\mvnw.cmd -Dmaven.test.skip=true spring-boot:run
cd frontend
npm run dev
```

Cấu hình storage từ terminal tại thư mục gốc: `powershell -ExecutionPolicy Bypass -File .\drill\configure-storage.ps1`. Drill Web Console: http://localhost:8047; backend: http://localhost:8080; OpenAPI: `/v3/api-docs`. Docker Compose hiện chỉ chạy Drill.

## Báo cáo và frontend

Các API bên dưới dành cho ADMIN/MANAGER, dùng JWT. Khoảng ngày tối đa 31 ngày, tham số `from`/`to` theo ISO `yyyy-MM-dd` và UTC. Tồn kho luôn tính theo ngày nghiệp vụ hiện tại của backend.

| API | Mục đích |
|---|---|
| `GET /api/analytics/dashboard?from=...&to=...` | Drill tổng hợp toàn bộ sổ thanh toán theo ngày và thống kê tồn FEFO; không giới hạn 100 đơn |
| `GET /api/analytics/inventory?page=0&size=50` | Tồn khả dụng, hết hạn và cách ly |
| `GET /api/analytics/cash-flow?from=...&to=...` | Tiền thu/hoàn từ bản xuất sự kiện Astra |
| `GET /api/analytics/reconciliation?from=...&to=...` | So sánh tiền, số giao dịch MySQL/Astra và tổng đơn/chi tiết đơn; `PASS`/`CHECK` |
| `GET /api/integrations/status` | Trạng thái hàng đợi đồng bộ; không phải kiểm tra sức khỏe Drill |
| `POST /api/integrations/catalog/sync` | Xếp catalog vào Outbox |
| `POST /api/integrations/dispatch` | Gửi dữ liệu đang chờ |
| `POST /api/integrations/astra/events/export?date=...` | Xuất một ngày sự kiện Astra ra JSON |

Tiền thu ròng = PAYMENT − REFUND; đây là dòng tiền, không đồng nghĩa lợi nhuận hay doanh thu kế toán. Dashboard mặc định 7 ngày UTC, đọc sổ MySQL qua Drill; báo cáo dòng tiền đọc sự kiện đã xuất nên có thể chậm hơn. Nếu Drill lỗi, KPI hiển thị chưa có số liệu thay vì số 0. `dataSources` trong cash-flow/reconciliation cho biết từng ngày dùng `JSON` hay `PARQUET`.

Chuyển một ngày đã xuất thành Parquet:

```powershell
powershell -ExecutionPolicy Bypass -File .\drill\curate-events-parquet.ps1 -Date 2026-10-08
```

Script tạo bảng phiên bản mới, chuẩn hóa kiểu, loại event mẫu và gộp event trùng ID; chỉ công bố manifest khi JSON không thay đổi trong lúc xử lý. Backend chọn Parquet khi SHA-256 khớp JSON hiện tại; nếu JSON có dữ liệu mới thì dùng JSON cho tới lần chuyển đổi tiếp theo. Bảng phiên bản cũ được giữ để không làm gián đoạn truy vấn đang đọc; cần quản lý dung lượng nếu chạy chuyển đổi nhiều lần. Hash được tính khi chọn nguồn, nên vẫn có chi phí đọc JSON trên host.

SQL demo JOIN MySQL/MongoDB/sự kiện nằm trong `drill/analytics-queries.sql`; SQL đối soát mẫu trong `drill/reconciliation-queries.sql`. Mẫu đối soát cố định ngày 2026-10-08. Script cấu hình storage cố định JDBC/session MySQL ở UTC để đọc TIMESTAMP đúng mốc của Astra.

Tài liệu thực trạng, rubric và việc còn lại: [docs/README.md](docs/README.md). Dữ liệu nhiều ngày, benchmark và slide/phân công nhóm vẫn cần minh chứng; không xem việc build thành công là đã kiểm thử nghiệp vụ.