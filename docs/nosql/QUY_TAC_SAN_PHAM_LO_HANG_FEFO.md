# Ghi chú chuyển nghiệp vụ sản phẩm, lô hàng và FEFO sang NoSQL

Phạm vi: giữ lại quy tắc quản lý sản phẩm và tồn kho để dùng trong project NoSQL. Đây là ghi chú chuyển giao, không phải xác nhận đã triển khai backend NoSQL.

## 1. Sản phẩm và lô hàng khác nhau thế nào?

Sản phẩm là loại hàng đang bán: mã sản phẩm, tên, giá bán, nhóm hàng và trạng thái kinh doanh. Lô hàng là một phần hàng cụ thể của sản phẩm đó: số lượng, ngày nhận, ngày sản xuất, hạn dùng và tình trạng. Một sản phẩm có thể có nhiều lô với các hạn dùng khác nhau; không tạo sản phẩm mới chỉ vì khác hạn dùng.

Ví dụ minh họa:

| Mã sản phẩm | Sản phẩm | Mã lô nội bộ | Ngày nhập | Hạn dùng | Số lượng |
|---|---|---|---|---|---:|
| 2 | Sữa không đường 1L | 10 | 14/10/2026 | 20/10/2026 | 30 |
| 2 | Sữa không đường 1L | 11 | 14/10/2026 | 30/10/2026 | 50 |

Đây là một sản phẩm, hai lô, tổng 80 hộp. Hạn dùng của hàng nhập được ghi theo bao bì/thông tin nhà cung cấp; ngày nhận hàng không làm thay đổi hạn dùng. Nhập khác ngày có thể cùng lô và cùng hạn; hai lô khác nhau cũng có thể trùng hạn.

Giá bán hiện tại thuộc sản phẩm trong phạm vi nghiệp vụ đã chọn. Giá vốn nhập hàng có thể khác giữa các lô. Khi bán, đơn hàng phải lưu đơn giá tại thời điểm bán để việc đổi giá sau này không làm thay đổi đơn cũ hoặc tiền hoàn.

## 2. Quy tắc FEFO cần giữ

FEFO nghĩa là ưu tiên hàng gần hết hạn trước. Chỉ xét lô đang hoạt động, còn hạn và còn lượng chưa giữ cho đơn khác. Tồn khả dụng của một lô bằng lượng còn lại trừ lượng đã giữ; tồn có thể bán của sản phẩm là tổng lượng khả dụng của các lô đủ điều kiện.

Thứ tự cấp phát hiện tại: hạn dùng tăng dần → ngày nhận tăng dần → ID lô tăng dần. Lô không có hạn dùng được xếp sau các lô có hạn. Nếu lô đầu không đủ, lấy tiếp từ lô kế tiếp; nếu tổng lượng hợp lệ không đủ thì từ chối toàn bộ yêu cầu cấp phát.

Trong ví dụ, bán 35 hộp khi hai lô đều đủ điều kiện sẽ lấy 30 hộp từ lô 10 và 5 hộp từ lô 11. Phải lưu lại từng dòng cấp phát để biết mỗi món của đơn đã lấy từ lô nào.

Code hiện tại cho phép lô có hạn dùng bằng ngày kiểm tra; loại lô có hạn nhỏ hơn ngày kiểm tra. Khi chuyển sang NoSQL phải giữ hoặc chủ động chốt lại quy tắc này, dùng một múi giờ thống nhất cho ngày nghiệp vụ. Ngày sản xuất và hạn dùng nên được mô hình hóa như ngày lịch, tránh lệch ngày khi chuyển đổi múi giờ.

## 3. Giữ hàng, xuất hàng, hủy và trả hàng

| Thao tác | Cách xử lý tồn cần giữ |
|---|---|
| Thêm vào giỏ | Chưa giữ hoặc trừ kho; kiểm tra lại khi checkout |
| Tạo đơn online | Giữ số lượng trên đúng các lô FEFO; lượng đã giữ không còn bán cho đơn khác |
| Xuất đơn online | Chuyển phần đã giữ thành phần đã xuất; kiểm tra lại tình trạng và hạn của lô |
| Bán tại POS | Xuất hàng và ghi nhận thanh toán trong cùng giao dịch |
| Hủy trước xuất / hết hạn chuyển khoản | Giải phóng phần đã giữ, không cộng thêm lượng hàng vật lý |
| Giao thất bại | Ghi nhận hàng đang quay về; chưa cộng kho khi chưa nhận thực tế |
| Nhận trả hàng | Giới hạn theo lượng đã mua trừ lượng đã trả; ghi rõ hàng bán được hay cách ly |
| Hoàn tiền | Theo khoản thực thu và phiếu trả; chống ghi nhận hoàn lặp hoặc chồng giữa toàn đơn và từng phiếu |

Hàng trả bán được phải về đúng lô còn hợp lệ. Hàng hết hạn/hỏng/cách ly không được cộng vào lượng có thể bán. Nếu trả một phần món đã xuất từ nhiều lô, phải xác định lô trên hàng thực tế; không đoán lô dựa vào tên sản phẩm. Đơn lịch sử không chứng minh được nguồn lô phải xử lý riêng, không tự tạo nguồn gốc hàng.

Nhân viên cần lấy đúng lô mà hệ thống cấp phát. Quét mã sản phẩm thông thường không đủ để chứng minh lô của món hàng đang cầm; cần kiểm tra nhãn lô/hạn và tổ chức vị trí lấy hàng phù hợp.

## 4. Gợi ý lưu trữ cho project NoSQL

Đây là phương án đề xuất nếu chọn MongoDB làm nơi lưu nghiệp vụ chính; collection catalog MongoDB đang có trong project hiện tại chỉ là bản đồng bộ, chưa thay thế MySQL cho giao dịch kho.

| Dữ liệu | Collection gợi ý | Nội dung chính |
|---|---|---|
| Sản phẩm | `products` | ID, SKU, tên, giá bán, nhóm, trạng thái |
| Lô | `inventory_batches` | ID, productId, mã lô, ngày nhận/sản xuất/hạn dùng, lượng còn/giữ/cách ly |
| Cấp phát | `batch_allocations` | orderItemId, batchId, số lượng cấp phát, số lượng đã trả, trạng thái |
| Đơn hàng | `orders` | Người mua, dòng hàng với đơn giá đã chốt, trạng thái tiền/giao hàng |
| Biến động kho | `stock_movements` | Nhập, giữ, xuất, giải phóng, trả, điều chỉnh và lý do |
| Phiếu trả / thanh toán | `returns`, `payments` | Số lượng nhận trả, khoản tiền thực thu/hoàn, mã yêu cầu/biên nhận |

Các thay đổi đơn, lô, cấp phát và tiền phải được ghi nhất quán. Khi dùng MongoDB cần cơ chế cập nhật có điều kiện và transaction phù hợp với cách tổ chức document; không chỉ đọc tồn rồi ghi đè vì hai khách có thể cùng mua phần hàng cuối. Giữ mã yêu cầu/biên nhận để xử lý gửi lại mà không trừ kho hoặc hoàn tiền hai lần. Có thể dùng lại `FefoPolicy` và `ReturnPolicy` là Java thuần; phần truy vấn, khóa/cập nhật và giao dịch phải viết lại cho bộ lưu trữ mới.

Một điểm cần thiết kế thêm khi chuyển: một lô sản xuất có thể được nhận ở nhiều phiếu nhập. Nên phân biệt mã lô nội bộ, mã lô nhà sản xuất và phiếu nhận hàng. API hiện tại xem việc nhập lại cùng mã lô với cùng nội dung là yêu cầu lặp, không cộng hàng thêm; vì vậy không mặc định coi nó đã hỗ trợ nhiều phiếu nhận riêng cho cùng lô sản xuất.

## 5. Tài liệu thực tế đã tham khảo

- [Odoo: quản lý hạn dùng theo lô và cấp phát FEFO](https://www.odoo.com/documentation/17.0/applications/inventory_and_mrp/inventory/product_management/product_tracking/expiration_dates.html).
- [GS1: phân biệt mã sản phẩm, mã lô và thông tin ngày trên nhãn logistics](https://www.gs1.org/standards/gs1-logistic-label-guideline/1-3).

Nguồn code đối chiếu: `FefoPolicy`, `ReturnPolicy`, `InventoryBatchService`, `InventoryBatchRepository` và `SalesOrderServiceImpl`. Các vấn đề giao diện đã nêu trong lần rà project vẫn cần xử lý; ghi chú này chỉ bảo toàn quy tắc nghiệp vụ để chuyển sang NoSQL.
