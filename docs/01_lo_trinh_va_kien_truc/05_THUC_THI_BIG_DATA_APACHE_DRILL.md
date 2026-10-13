# Demo và bàn giao đề tài Apache Drill

Cách khởi động, tên database/storage và các API hiện hành nằm trong README gốc. Tài liệu này là kịch bản đề xuất, không phải biên bản đã kiểm thử.

| Bước demo | Thao tác | Ý nghĩa cần giải thích |
|---|---|---|
| 1 | Giới thiệu MySQL, MongoDB catalog và bảng sự kiện Cassandra | Mỗi nguồn có nhiệm vụ riêng; MySQL quyết định tiền và tồn |
| 2 | Tạo/thu tiền một đơn, quan sát Outbox | Giao dịch chính không phụ thuộc việc cloud phản hồi ngay |
| 3 | Đồng bộ catalog/sự kiện và export ngày UTC | Dữ liệu phân tích có độ trễ, Cassandra đi qua JSON |
| 4 | Chạy SQL trong drill/analytics-queries.sql | Drill JOIN nguồn khác nhau bằng SKU/order_id |
| 5 | Chạy curate-events-parquet.ps1 cho ngày đã xuất | Tạo bản Parquet/Snappy và manifest nguồn |
| 6 | Mở báo cáo dòng tiền/đối soát | dataSources thể hiện nguồn; giải thích delta và PASS/CHECK |
| 7 | Mở dashboard | Tổng tiền theo ledger đầy đủ, trừ hoàn tiền, tồn loại lô hết hạn/không ACTIVE |

JSON thay đổi làm hash khác manifest: backend dùng JSON cho tới khi curate lại. Bản Parquet cũ thiếu manifest không được tự nhận là bản cập nhật. Khi gọi báo cáo chưa có bản xuất, hiển thị chưa có số liệu; không diễn giải thành đã thu 0 đồng.

Bàn giao bên kiểm thử: xác nhận đơn nhiều món không nhân tổng tiền; hoàn tiền một phần/hủy đơn được tính đúng ngày; hơn 100 đơn không thiếu số liệu; lô hết hạn/giữ hàng không vào tồn khả dụng; JSON và Parquet cùng snapshot có tổng tiền/số sự kiện giống nhau; JSON được export mới thì nguồn quay về JSON. Không chạy các bước kiểm thử này trong đợt triển khai theo yêu cầu chủ dự án.

Benchmark do bên phụ trách riêng thực hiện: dùng cùng snapshot đã lọc mẫu và trùng ID, cùng truy vấn/cột/bộ lọc; ghi số dòng và kích thước, số lần chạy, cold/warm cache, thời gian và Query Profiles. Hash-selection trên backend có chi phí đọc JSON; đo riêng Drill query và API nếu so sánh hiệu năng. Không suy ra tốc độ truy vấn chỉ từ dung lượng nén.

Mẫu phân công bên dưới chưa điền người và chưa xác nhận hoàn thành:

| Công việc | Thành viên | Minh chứng bàn giao |
|---|---|---|
| Nghiệp vụ/MySQL | Chưa điền | Migration, luồng giao dịch |
| MongoDB/Cassandra/Outbox | Chưa điền | Mô hình dữ liệu, đồng bộ |
| Drill/SQL/Parquet | Chưa điền | SQL, manifest, Profiles |
| Frontend | Chưa điền | Dashboard, bảng đối soát |
| Kiểm thử/benchmark | Chưa điền | Kết quả đo và lỗi đã xác minh |
| Báo cáo/slide/demo | Chưa điền | Báo cáo cuối, phân công thuyết trình |