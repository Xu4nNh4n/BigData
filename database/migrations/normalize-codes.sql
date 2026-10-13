-- Chuẩn hóa mã hiển thị về số ngắn, giữ nguyên các ID kỹ thuật.
-- Chạy sau khi schema đã được Liquibase tạo và trước khi đồng bộ lại catalog/events.
USE supermarket_api;

START TRANSACTION;

CREATE TEMPORARY TABLE code_map_products AS
SELECT product_id, sku_code AS old_code,
       CAST(ROW_NUMBER() OVER (ORDER BY product_id) AS CHAR) AS new_code
FROM products;
CREATE TEMPORARY TABLE code_map_batches AS
SELECT batch_id, batch_code AS old_code,
       CAST(ROW_NUMBER() OVER (ORDER BY batch_id) AS CHAR) AS new_code
FROM inventory_batches;
CREATE TEMPORARY TABLE code_map_orders AS
SELECT order_id, order_code AS old_code,
       CAST(ROW_NUMBER() OVER (ORDER BY order_id) AS CHAR) AS new_code
FROM sales_orders;

-- Dùng mã tạm trước để không va chạm UNIQUE khi đổi nhiều dòng sang 1, 2, 3...
UPDATE products p
JOIN code_map_products m ON m.product_id = p.product_id
SET p.sku_code = CONCAT('TMP-SKU-', p.product_id);
UPDATE products p
JOIN code_map_products m ON m.product_id = p.product_id
SET p.sku_code = m.new_code;

UPDATE inventory_batches b
JOIN code_map_batches m ON m.batch_id = b.batch_id
SET b.batch_code = CONCAT('TMP-BATCH-', b.batch_id);
UPDATE inventory_batches b
JOIN code_map_batches m ON m.batch_id = b.batch_id
SET b.batch_code = m.new_code;

UPDATE sales_orders o
JOIN code_map_orders m ON m.order_id = o.order_id
SET o.order_code = CONCAT('TMP-ORDER-', o.order_id);
UPDATE sales_orders o
JOIN code_map_orders m ON m.order_id = o.order_id
SET o.order_code = m.new_code;

-- Đồng bộ các payload chờ gửi và ghi chú kho để không còn mã cũ trong outbox.
UPDATE data_sync_outbox o
JOIN code_map_products m ON o.payload LIKE CONCAT('%', m.old_code, '%')
SET o.payload = REPLACE(o.payload, m.old_code, m.new_code);
UPDATE data_sync_outbox o
JOIN code_map_batches m ON o.payload LIKE CONCAT('%', m.old_code, '%')
SET o.payload = REPLACE(o.payload, m.old_code, m.new_code);
UPDATE data_sync_outbox o
JOIN code_map_orders m ON o.payload LIKE CONCAT('%', m.old_code, '%')
SET o.payload = REPLACE(o.payload, m.old_code, m.new_code);
UPDATE stock_movements s
JOIN code_map_batches m ON s.note LIKE CONCAT('%', m.old_code, '%')
SET s.note = REPLACE(s.note, m.old_code, m.new_code);

DROP TEMPORARY TABLE code_map_products;
DROP TEMPORARY TABLE code_map_batches;
DROP TEMPORARY TABLE code_map_orders;

COMMIT;
