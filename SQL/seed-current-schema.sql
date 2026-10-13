-- Seed dữ liệu nền cho schema hiện tại sau khi Liquibase đã chạy.
-- Không chạy file này trước khi database supermarket_api đã được Liquibase tạo bảng.
-- Mật khẩu các tài khoản mẫu là: password

USE supermarket_api;

START TRANSACTION;

INSERT INTO roles (role_name)
VALUES
    ('ADMIN'),
    ('STAFF'),
    ('CUSTOMER'),
    ('MANAGER'),
    ('WAREHOUSE')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- BCrypt của chuỗi password.
INSERT INTO users (username, password_hash, full_name, email, phone, role_id, is_active)
SELECT 'seed_admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
       'Seed Administrator', 'seed.admin@supermarket.local', '0900000001', r.role_id, TRUE
FROM roles r WHERE r.role_name = 'ADMIN'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), is_active = TRUE;

INSERT INTO users (username, password_hash, full_name, email, phone, role_id, is_active)
SELECT 'seed_staff', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
       'Seed Sales Staff', 'seed.staff@supermarket.local', '0900000002', r.role_id, TRUE
FROM roles r WHERE r.role_name = 'STAFF'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), is_active = TRUE;

INSERT INTO users (username, password_hash, full_name, email, phone, role_id, is_active)
SELECT 'seed_customer', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
       'Seed Online Customer', 'seed.customer@supermarket.local', '0900000003', r.role_id, TRUE
FROM roles r WHERE r.role_name = 'CUSTOMER'
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), is_active = TRUE;

INSERT INTO customers (full_name, email, phone, address, is_active, user_id)
SELECT 'Seed Online Customer', 'seed.customer@supermarket.local', '0900000003',
       '123 Đường Dữ Liệu, Thành phố Hồ Chí Minh', TRUE, u.user_id
FROM users u WHERE u.username = 'seed_customer'
ON DUPLICATE KEY UPDATE address = VALUES(address), is_active = TRUE;

INSERT INTO categories (name)
VALUES
    ('Đồ uống'),
    ('Thực phẩm khô'),
    ('Sữa và chế phẩm'),
    ('Đồ gia dụng'),
    ('Rau củ quả')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO suppliers (name, phone, email, address, is_active)
VALUES
    ('Nhà cung cấp Fresh Farm', '0280000001', 'fresh.farm@supermarket.local', 'Thành phố Hồ Chí Minh', TRUE),
    ('Nhà cung cấp Daily Food', '0280000002', 'daily.food@supermarket.local', 'Bình Dương', TRUE),
    ('Nhà cung cấp Home Goods', '0280000003', 'home.goods@supermarket.local', 'Đồng Nai', TRUE)
ON DUPLICATE KEY UPDATE phone = VALUES(phone), email = VALUES(email), is_active = TRUE;

-- Sản phẩm bắt đầu với tồn tổng bằng 0; tồn thật được nhập qua inventory_batches bên dưới.
INSERT INTO products
    (name, sku_code, price, stock_quantity, reserved_quantity, damaged_quantity, version,
     reorder_point, expiry_date, image_url, is_active, category_id, supplier_id)
SELECT 'Nước suối 500ml', '1', 5000, 0, 0, 0, 0, 20, NULL, NULL, TRUE,
       c.category_id, s.supplier_id
FROM categories c CROSS JOIN suppliers s
WHERE c.name = 'Đồ uống' AND s.name = 'Nhà cung cấp Fresh Farm'
ON DUPLICATE KEY UPDATE price = VALUES(price), is_active = TRUE;

INSERT INTO products
    (name, sku_code, price, stock_quantity, reserved_quantity, damaged_quantity, version,
     reorder_point, expiry_date, image_url, is_active, category_id, supplier_id)
SELECT 'Sữa tươi không đường 1L', '2', 32000, 0, 0, 0, 0, 10, NULL, NULL, TRUE,
       c.category_id, s.supplier_id
FROM categories c CROSS JOIN suppliers s
WHERE c.name = 'Sữa và chế phẩm' AND s.name = 'Nhà cung cấp Fresh Farm'
ON DUPLICATE KEY UPDATE price = VALUES(price), is_active = TRUE;

INSERT INTO products
    (name, sku_code, price, stock_quantity, reserved_quantity, damaged_quantity, version,
     reorder_point, expiry_date, image_url, is_active, category_id, supplier_id)
SELECT 'Mì gói vị bò hầm', '3', 6500, 0, 0, 0, 0, 30, NULL, NULL, TRUE,
       c.category_id, s.supplier_id
FROM categories c CROSS JOIN suppliers s
WHERE c.name = 'Thực phẩm khô' AND s.name = 'Nhà cung cấp Daily Food'
ON DUPLICATE KEY UPDATE price = VALUES(price), is_active = TRUE;

INSERT INTO products
    (name, sku_code, price, stock_quantity, reserved_quantity, damaged_quantity, version,
     reorder_point, expiry_date, image_url, is_active, category_id, supplier_id)
SELECT 'Nồi inox 18cm', '4', 185000, 0, 0, 0, 0, 5, NULL, NULL, TRUE,
       c.category_id, s.supplier_id
FROM categories c CROSS JOIN suppliers s
WHERE c.name = 'Đồ gia dụng' AND s.name = 'Nhà cung cấp Home Goods'
ON DUPLICATE KEY UPDATE price = VALUES(price), is_active = TRUE;

INSERT INTO products
    (name, sku_code, price, stock_quantity, reserved_quantity, damaged_quantity, version,
     reorder_point, expiry_date, image_url, is_active, category_id, supplier_id)
SELECT 'Táo đỏ nhập khẩu', '5', 45000, 0, 0, 0, 0, 15, NULL, NULL, TRUE,
       c.category_id, s.supplier_id
FROM categories c CROSS JOIN suppliers s
WHERE c.name = 'Rau củ quả' AND s.name = 'Nhà cung cấp Fresh Farm'
ON DUPLICATE KEY UPDATE price = VALUES(price), is_active = TRUE;

-- Nhập các lô mẫu để thử FEFO. Mỗi batch_code là duy nhất.
INSERT INTO inventory_batches
    (product_id, supplier_id, batch_code, received_quantity, remaining_quantity,
     reserved_quantity, damaged_quantity, unit_cost, manufacturing_date, expiry_date,
     received_by, status)
SELECT p.product_id, p.supplier_id, '1', 100, 100, 0, 0, 2500,
       '2026-09-01', '2027-09-01', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '1'
ON DUPLICATE KEY UPDATE batch_code = batch_code;

INSERT INTO inventory_batches
    (product_id, supplier_id, batch_code, received_quantity, remaining_quantity,
     reserved_quantity, damaged_quantity, unit_cost, manufacturing_date, expiry_date,
     received_by, status)
SELECT p.product_id, p.supplier_id, '2', 40, 40, 0, 0, 2300,
       '2026-08-01', '2026-12-01', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '1'
ON DUPLICATE KEY UPDATE batch_code = batch_code;

INSERT INTO inventory_batches
    (product_id, supplier_id, batch_code, received_quantity, remaining_quantity,
     reserved_quantity, damaged_quantity, unit_cost, manufacturing_date, expiry_date,
     received_by, status)
SELECT p.product_id, p.supplier_id, '3', 60, 60, 0, 0, 21000,
       '2026-09-20', '2026-11-20', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '2'
ON DUPLICATE KEY UPDATE batch_code = batch_code;

INSERT INTO inventory_batches
    (product_id, supplier_id, batch_code, received_quantity, remaining_quantity,
     reserved_quantity, damaged_quantity, unit_cost, manufacturing_date, expiry_date,
     received_by, status)
SELECT p.product_id, p.supplier_id, '4', 120, 120, 0, 0, 4200,
       '2026-09-15', '2027-09-15', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '3'
ON DUPLICATE KEY UPDATE batch_code = batch_code;

INSERT INTO inventory_batches
    (product_id, supplier_id, batch_code, received_quantity, remaining_quantity,
     reserved_quantity, damaged_quantity, unit_cost, manufacturing_date, expiry_date,
     received_by, status)
SELECT p.product_id, p.supplier_id, '5', 25, 25, 0, 0, 120000,
       NULL, NULL, 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '4'
ON DUPLICATE KEY UPDATE batch_code = batch_code;

INSERT INTO inventory_batches
    (product_id, supplier_id, batch_code, received_quantity, remaining_quantity,
     reserved_quantity, damaged_quantity, unit_cost, manufacturing_date, expiry_date,
     received_by, status)
SELECT p.product_id, p.supplier_id, '6', 50, 50, 0, 0, 28000,
       '2026-09-25', '2026-11-05', 'seed_admin', 'ACTIVE'
FROM products p WHERE p.sku_code = '5'
ON DUPLICATE KEY UPDATE batch_code = batch_code;

-- Đồng bộ tồn tổng của Product với tổng số lượng đã nhập từ các lô seed.
UPDATE products p
SET p.stock_quantity = (
        SELECT COALESCE(SUM(b.remaining_quantity), 0)
        FROM inventory_batches b WHERE b.product_id = p.product_id
    )
WHERE p.sku_code IN ('1', '2', '3', '4', '5');

INSERT INTO stock_movements (product_id, movement_type, quantity, note, performed_by)
SELECT p.product_id, 'OPENING', SUM(b.received_quantity), 'Dữ liệu seed các lô nhập ban đầu', 'seed_admin'
FROM products p JOIN inventory_batches b ON b.product_id = p.product_id
WHERE p.sku_code IN ('1', '2', '3', '4', '5')
GROUP BY p.product_id
ON DUPLICATE KEY UPDATE note = VALUES(note);

COMMIT;
