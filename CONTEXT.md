# Supermarket Sales and Inventory Context

Hệ thống quản lý danh mục hàng hóa, tồn kho theo lô và hoạt động bán hàng tại
quầy hoặc trực tuyến. Tài liệu này thống nhất ngôn ngữ nghiệp vụ được sử dụng
trong mã nguồn, tài liệu và trao đổi của dự án.

## Identity and Customer

**User**:
Tài khoản dùng để đăng nhập và được cấp quyền thông qua một Role. User có thể
đại diện cho nhân viên hoặc khách hàng nhưng không thay thế hồ sơ Customer.
_Avoid_: Account, Login

**Role**:
Nhóm quyền nghiệp vụ được gán cho User, chẳng hạn ADMIN, STAFF, WAREHOUSE hoặc
CUSTOMER.
_Avoid_: UserType, PermissionGroup

**Customer**:
Hồ sơ người mua dùng để lưu thông tin liên hệ, điểm thành viên và lịch sử mua
hàng. Khách mua tại quầy có thể có Customer mà không có User.
_Avoid_: Client, Buyer, Account

**Guest Session**:
Danh tính tạm thời của khách chưa đăng nhập, dùng để liên kết giỏ hàng và hành
vi phát sinh trong một khoảng thời gian.
_Avoid_: Anonymous Customer

## Product Catalog

**Product**:
Mặt hàng kinh doanh được nhận diện bằng một SKU và có giá bán hiện tại. Product
không đại diện cho một lần nhập hàng và không sở hữu hạn sử dụng riêng.
_Avoid_: Batch, Stock Item

**SKU**:
Mã nghiệp vụ duy nhất nhận diện một Product trong danh mục hàng hóa.
_Avoid_: Batch Code, Product ID

**Category**:
Nhóm phân loại chính của Product dùng cho quản lý, tìm kiếm và báo cáo.
_Avoid_: Tag

**Supplier**:
Đơn vị cung cấp hàng hóa cho siêu thị. Một Product có thể được nhập từ Supplier
thông qua các lần nhận hàng.
_Avoid_: Manufacturer

**Product Detail**:
Thông tin mô tả mở rộng của Product, bao gồm hình ảnh và thuộc tính thay đổi theo
từng loại hàng hóa. Giá bán và tồn kho không thuộc Product Detail.
_Avoid_: Product, Product Master

## Inventory

**Inventory Batch**:
Một lô hàng cụ thể của Product được tiếp nhận vào kho, có mã lô, số lượng còn
lại, giá vốn và hạn sử dụng riêng.
_Avoid_: Product, Shipment

**Batch Code**:
Mã nghiệp vụ nhận diện duy nhất một Inventory Batch.
_Avoid_: SKU, Product Code

**Stock on Hand**:
Tổng số lượng còn lại của các Inventory Batch hợp lệ thuộc cùng một Product.
_Avoid_: Imported Quantity

**Stock Movement**:
Một lần tăng hoặc giảm số lượng của Inventory Batch do nhập hàng, bán hàng, hủy
đơn, kiểm kê, hư hỏng hoặc hết hạn.
_Avoid_: Stock Value, Inventory Batch

**FEFO**:
Chính sách xuất kho ưu tiên Inventory Batch có hạn sử dụng gần nhất nhưng vẫn
còn hợp lệ.
_Avoid_: FIFO

**Expired Batch**:
Inventory Batch đã qua hạn sử dụng và không còn được phép cấp phát cho đơn hàng.
_Avoid_: Inactive Product

## Cart and Ordering

**Cart**:
Danh sách lựa chọn tạm thời của một Customer hoặc Guest Session trước khi đặt
đơn. Giá hiển thị trong Cart không phải giá bán được cam kết.
_Avoid_: Order, Invoice

**Cart Item**:
Một Product và số lượng mà người mua đang dự định đặt trong Cart.
_Avoid_: Sales Order Item

**Sales Order**:
Cam kết bán hàng được tạo tại quầy hoặc trực tuyến, chứa tổng tiền, trạng thái
và một hoặc nhiều Sales Order Item.
_Avoid_: Cart, Invoice

**Sales Order Item**:
Dòng hàng trong Sales Order, lưu Product, số lượng và đơn giá tại thời điểm đặt
để đơn cũ không thay đổi khi giá Product thay đổi.
_Avoid_: Cart Item, Product

**Batch Allocation**:
Phần số lượng của một Sales Order Item được cấp phát từ một Inventory Batch cụ
thể. Một Sales Order Item có thể có nhiều Batch Allocation.
_Avoid_: Sales Order Item, Stock Movement

**Order Type**:
Kênh phát sinh Sales Order, gồm IN_STORE tại quầy và ONLINE trên website.
_Avoid_: Payment Method

**Order Status**:
Trạng thái nghiệp vụ hiện tại của Sales Order trong vòng đời xác nhận, chuẩn bị,
giao hàng, hoàn tất hoặc hủy.
_Avoid_: Payment Status, Delivery Status

**Cancellation**:
Việc kết thúc một Sales Order chưa hoàn tất và hoàn lại các Batch Allocation đã
cấp phát. Một Sales Order chỉ được hoàn kho một lần.
_Avoid_: Deletion, Return

## Fulfillment

**Delivery**:
Thông tin giao nhận gắn với Sales Order trực tuyến, lưu ảnh chụp người nhận, số
điện thoại và địa chỉ tại thời điểm đặt hàng.
_Avoid_: Customer Address, Shipment Batch

**Delivery Status**:
Trạng thái thực hiện giao nhận, tách biệt với trạng thái thanh toán của Sales
Order.
_Avoid_: Order Status, Payment Status

## Customer Behaviour

**Customer Event**:
Bản ghi bất biến mô tả một hành vi của Customer hoặc Guest Session, chẳng hạn
xem sản phẩm, tìm kiếm, thay đổi giỏ hàng hoặc hoàn tất đơn.
_Avoid_: Audit Log, Stock Movement

**Recommendation**:
Danh sách Product được xếp hạng cho một Customer dựa trên quan hệ xem, thêm vào
giỏ hoặc mua hàng; đây không phải cam kết về giá hay tồn kho.
_Avoid_: Promotion, Search Result
