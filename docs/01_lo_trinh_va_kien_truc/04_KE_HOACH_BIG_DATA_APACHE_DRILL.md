# Đối chiếu rubric môn Big Data

Rubric do chủ dự án cung cấp có sáu tiêu chí. Dải 85–100% tương ứng mức 8,5–10, nhưng chưa kèm yêu cầu chi tiết hoặc số dòng tối thiểu; không dùng số lượng database/API để suy ra điểm.

| Tiêu chí | Trọng số | Source hiện có | Minh chứng cần hoàn thiện |
|---|---:|---|---|
| Ứng dụng kỹ thuật | 10% | Bài toán vận hành siêu thị, tồn FEFO, dòng tiền và đối soát | Giải thích kết quả giúp phát hiện thiếu hàng hoặc lệch tiền như thế nào |
| Lưu trữ/xử lý dữ liệu | 20% | MySQL, catalog MongoDB, Cassandra theo ngày, JSON/Parquet | Mô hình dữ liệu, dữ liệu nhiều ngày, chất lượng và độ trễ |
| Mô hình/công nghệ | 25% | Federated SQL Drill, Outbox, Parquet/Snappy, chọn nguồn theo hash | SQL JOIN nhiều nguồn, Profiles và benchmark cùng dữ liệu/query |
| Trực quan hóa/ứng dụng | 25% | React dashboard từ Drill và báo cáo đối soát | Ảnh/screen recording, số liệu đúng toàn bộ dataset, kết luận từ kết quả |
| Báo cáo | 10% | README và tài liệu phản ánh phạm vi hiện tại | Báo cáo cuối, slide, hạn chế và tài liệu tham khảo |
| Phối hợp nhóm | 10% | Không thể xác nhận chỉ qua source | Thành viên, công việc, đóng góp, người demo và lịch sử phối hợp |

Ba tiêu chí lưu trữ, công nghệ và ứng dụng chiếm 70%; ưu tiên hoàn thiện bằng chứng ở đây. Có cấu trúc mã nguồn không đồng nghĩa đã đạt đủ yêu cầu hoặc đảm bảo mức điểm cụ thể.

Tài liệu phản ánh MySQL giao dịch, MongoDB catalog và Cassandra sự kiện. Không yêu cầu tích hợp thêm database, Metabase/Superset hoặc funnel hành vi khi không thuộc phạm vi đã chốt. Không đưa con số cải thiện hiệu năng vào báo cáo trước khi bên phụ trách đo và lưu kết quả.