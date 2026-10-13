# Thực trạng triển khai Big Data

Đề tài: Phân tích dữ liệu vận hành siêu thị đa nguồn bằng Apache Drill. Nghiệp vụ bán hàng tạo dữ liệu; trọng tâm đánh giá là cách lưu trữ, truy vấn đa nguồn, đối soát và trình bày kết quả.

| Phần | Đã triển khai trong source | Giới hạn còn lại |
|---|---|---|
| Nghiệp vụ | POS, online COD/chuyển khoản, kho lô/FEFO, hủy, trả từng món, hoàn tiền | Kiểm thử nghiệp vụ bàn giao bên khác |
| Đồng bộ | Outbox cùng giao dịch MySQL, dispatcher gửi MongoDB/Astra, retry | Có độ trễ, phải trình bày số liệu chờ gửi |
| Truy vấn đa nguồn | SQL JOIN SKU MySQL/MongoDB và sự kiện xuất từ Cassandra | Hai API catalog/inventory-events đã bỏ; demo JOIN bằng SQL giữ trong drill |
| Dashboard | Drill tổng hợp sổ tiền toàn bộ khoảng ngày, thống kê tồn theo lô; frontend biểu đồ 7 ngày UTC | Không đồng nhất tiền thu ròng với lợi nhuận; chưa có top bán chạy theo danh mục |
| Đối soát | Tổng tiền đơn cộng riêng với dòng hàng; đếm và cộng thu/hoàn MySQL so với Astra; ngày UTC thống nhất | Sai lệch có thể do đồng bộ/export chưa xong; cần xác minh nguyên nhân trước sửa dữ liệu |
| Parquet | CTAS/Snappy, kiểu dữ liệu rõ, loại mẫu/ID trùng, manifest SHA-256; API ưu tiên bản khớp JSON | Bản cũ giữ lại; chưa tự động curate, chưa có benchmark hiệu năng |
| Giao diện | KPI, biểu đồ, đối soát và nguồn JSON/Parquet; lỗi báo cáo không giả thành số 0 | Cần bên kiểm thử xác nhận giao diện và SQL trên dữ liệu nhiều ngày |

Luồng đã chọn: MySQL → Outbox → MongoDB/Astra → export JSON → Parquet → Drill REST → backend → frontend. MongoDB là catalog, không phải kho log click. Drill không đọc Cassandra trực tiếp; không cần thêm Spark, Redis, Neo4j hoặc công cụ dashboard khác cho phạm vi hiện tại.

Việc tiếp theo: chuẩn bị dữ liệu nhiều ngày với thanh toán, hoàn tiền và trạng thái đa dạng; mở rộng câu hỏi phân tích trong phạm vi đã chốt; giao bên kiểm thử đo cùng dataset/query giữa JSON và Parquet; lưu Profiles, ảnh dashboard và kết quả đối soát vào báo cáo. Sau đó hoàn thiện slide và phân công nhóm. Không đặt số lượng dòng bắt buộc khi rubric môn học chưa quy định.

Không tự backdate dữ liệu giao dịch thật hoặc nhân bản sự kiện để đối soát giả đạt PASS. Nếu tạo dữ liệu mô phỏng quy mô lớn, dùng môi trường riêng, ghi rõ nguồn mô phỏng và giữ quan hệ đơn/chi tiết/thanh toán/lô hàng nhất quán.