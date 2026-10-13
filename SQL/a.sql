-- ==============================================================================
-- 🛒 SUPERMARKET MANAGEMENT SYSTEM - TOÀN BỘ CSDL MYSQL CHUẨN 100% VỚI BACKEND
-- ==============================================================================
-- File khởi tạo mẫu cũ. Schema nghiệp vụ hiện tại do Liquibase quản lý.
-- Một số tên cột cũ được migration 011 đổi tên khi ứng dụng khởi động.
-- File này có DROP TABLE: chỉ dành cho database phát triển muốn khởi tạo lại.
-- Không chạy để nâng cấp database đang có dữ liệu; xem docs/05_nghiep_vu_va_dto/TRIEN_KHAI_NGHIEP_VU_POS_ONLINE.md.
-- ==============================================================================

-- 1. Tạo database nếu chưa có và chuyển sang sử dụng
CREATE DATABASE IF NOT EXISTS supermarket_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE supermarket_db;

-- 2. Xóa các bảng cũ theo thứ tự khóa ngoại để tránh lỗi (nếu muốn reset)
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS sales_order_items;
DROP TABLE IF EXISTS sales_orders;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS suppliers;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS roles;
SET FOREIGN_KEY_CHECKS = 1;

-- ==============================================================================
-- 1. BẢNG ROLES (VAI TRÒ / PHÂN QUYỀN)
-- ==============================================================================
CREATE TABLE roles (
    role_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 2. BẢNG CATEGORIES (DANH MỤC SẢN PHẨM)
-- ==============================================================================
CREATE TABLE categories (
    category_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 3. BẢNG SUPPLIERS (NHÀ CUNG CẤP)
-- ==============================================================================
CREATE TABLE suppliers (
    supplier_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_name VARCHAR(150) NOT NULL,
    supplier_phone VARCHAR(20) UNIQUE,
    supplier_email VARCHAR(100) UNIQUE,
    supplier_address VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 4. BẢNG USERS (TÀI KHOẢN NHÂN VIÊN & QUẢN TRỊ VIÊN)
-- ==============================================================================
CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_roles FOREIGN KEY (role_id) REFERENCES roles(role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 5. BẢNG PRODUCTS (SẢN PHẨM & TỒN KHO)
-- ==============================================================================
CREATE TABLE products (
    product_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku_code VARCHAR(50) NOT NULL UNIQUE,
    product_name VARCHAR(150) NOT NULL,
    price DECIMAL(18,2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    reorder_point INT NOT NULL DEFAULT 10,
    expiry_date DATE,
    image_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    category_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_categories FOREIGN KEY (category_id) REFERENCES categories(category_id),
    CONSTRAINT fk_products_suppliers FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 6. BẢNG CUSTOMERS (KHÁCH HÀNG)
-- ==============================================================================
CREATE TABLE customers (
    customer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(15) UNIQUE,
    email VARCHAR(100) UNIQUE,
    address VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 7. BẢNG SALES_ORDERS (HÓA ĐƠN BÁN HÀNG - MASTER)
-- ==============================================================================
CREATE TABLE sales_orders (
    order_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code VARCHAR(50) NOT NULL UNIQUE,
    order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    customer_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    subtotal DECIMAL(18,2) DEFAULT 0.00,
    discount_amount DECIMAL(18,2) DEFAULT 0.00,
    total_amount DECIMAL(18,2) DEFAULT 0.00,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    note VARCHAR(255),
    order_type VARCHAR(20) NOT NULL DEFAULT 'IN_STORE',
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_sales_orders_customers FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    CONSTRAINT fk_sales_orders_users FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 8. BẢNG SALES_ORDER_ITEMS (CHI TIẾT MÓN HÀNG - DETAIL)
-- ==============================================================================
CREATE TABLE sales_order_items (
    order_item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(18,2) NOT NULL,
    subtotal DECIMAL(18,2) NOT NULL,
    CONSTRAINT fk_order_items_orders FOREIGN KEY (order_id) REFERENCES sales_orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_products FOREIGN KEY (product_id) REFERENCES products(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 9. BẢNG REFRESH_TOKENS (LƯU TRỮ TOKEN XOAY VÒNG ROTATION)
-- ==============================================================================
CREATE TABLE refresh_tokens (
    token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ==============================================================================
-- 📊 DỮ LIỆU KHỞI TẠO MẪU (SEED DATA SẴN SÀNG ĐỂ CHẠY & TEST)
-- ==============================================================================

-- 1. Thêm Roles
INSERT INTO roles (role_id, role_name, description) VALUES
(1, 'ROLE_ADMIN', 'Quản trị viên toàn quyền hệ thống'),
(2, 'ROLE_STAFF', 'Nhân viên thu ngân bán hàng');

-- 2. Thêm Users mẫu (Mật khẩu mặc định: admin123 và staff123 - Đã mã hóa BCrypt)
-- Hash BCrypt của 'admin123': $2a$10$7vNqv2KxG1fL6a6p0Hq.qe3VpB2B7yZc9R8s1z0b7.QoJ8qM1g7qK
-- Hash BCrypt của 'staff123': $2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi
INSERT INTO users (user_id, full_name, username, password_hash, email, phone, role_id, is_active) VALUES
(1, 'Quản Trị Viên', 'admin', '$2a$10$7vNqv2KxG1fL6a6p0Hq.qe3VpB2B7yZc9R8s1z0b7.QoJ8qM1g7qK', 'admin@supermarket.com', '0901234567', 1, TRUE),
(2, 'Thu Ngân 01', 'staff', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'staff01@supermarket.com', '0909876543', 2, TRUE);

-- 3. Thêm Categories
INSERT INTO categories (category_id, category_name, description, is_active) VALUES
(1, 'Nước giải khát', 'Nước ngọt, nước suối, trà đóng chai', TRUE),
(2, 'Bánh kẹo & Snack', 'Bánh quy, bim bim, kẹo sô-cô-la', TRUE),
(3, 'Sữa & Sản phẩm từ sữa', 'Sữa tươi, sữa chua, phô mai', TRUE),
(4, 'Thực phẩm đông lạnh', 'Cá viên, xúc xích, bao tử cá', TRUE);

-- 4. Thêm Suppliers
INSERT INTO suppliers (supplier_id, supplier_name, supplier_phone, supplier_email, supplier_address, is_active) VALUES
(1, 'Công ty TNHH Nước Giải Khát Suntory Pepsico', '02838222333', 'pepsico@vn.com', 'Cao ốc Đồng Khởi, Q1, TP.HCM', TRUE),
(2, 'Công ty Cổ phần Sữa Việt Nam (Vinamilk)', '02854155555', 'vinamilk@vinamilk.com.vn', 'Số 10 Tân Trào, Q7, TP.HCM', TRUE),
(3, 'Tập đoàn Masan Consumer', '02862555660', 'masan@masangroup.com', 'Tầng 12 Central Plaza, Lê Duẩn, Q1, TP.HCM', TRUE);

-- 5. Thêm Products
INSERT INTO products (product_id, sku_code, product_name, price, stock_quantity, reorder_point, expiry_date, image_url, category_id, supplier_id, is_active) VALUES
(1, '1', 'Nước Ngọt Pepsi Lon 330ml', 10000.00, 150, 20, '2026-12-31', 'https://example.com/pepsi.jpg', 1, 1, TRUE),
(2, '2', 'Nước Tăng Lực Sting Dâu 330ml', 11000.00, 200, 30, '2026-11-30', 'https://example.com/sting.jpg', 1, 1, TRUE),
(3, '3', 'Sữa Tươi Tiệt Trùng Vinamilk 100% 1L', 36000.00, 80, 15, '2026-09-30', 'https://example.com/vinamilk1l.jpg', 3, 2, TRUE),
(4, '4', 'Mì Khoai Tây Omachi Xốt Bò Hầm', 9500.00, 300, 50, '2026-10-15', 'https://example.com/omachi.jpg', 2, 3, TRUE);

-- 6. Thêm Customers mẫu
INSERT INTO customers (customer_id, full_name, phone, email, address, is_active) VALUES
(1, 'Nguyễn Văn An', '0912345678', 'an.nguyen@gmail.com', '123 Cách Mạng Tháng 8, Q3, TP.HCM', TRUE),
(2, 'Trần Thị Mai', '0987654321', 'mai.tran@yahoo.com', '456 Nguyễn Thị Minh Khai, Q1, TP.HCM', TRUE);
