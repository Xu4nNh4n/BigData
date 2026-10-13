-- Astra DB: tạo keyspace supermarket_analytics trước trong Astra Portal.
-- Astra DB không hỗ trợ CREATE KEYSPACE trong CQL Console.

CREATE TABLE IF NOT EXISTS supermarket_analytics.business_events_by_day (
    event_date date,
    event_time timestamp,
    event_id uuid,
    event_type text,
    order_id bigint,
    product_sku text,
    quantity int,
    amount decimal,
    payment_method text,
    status text,
    metadata text,
    PRIMARY KEY ((event_date), event_time, event_id)
) WITH CLUSTERING ORDER BY (event_time DESC);

-- Các event mẫu dùng để kiểm tra cấu trúc. Event bán hàng thật phải được ghi
-- sau khi đơn hàng đã được tạo và thanh toán thành công ở MySQL.
INSERT INTO supermarket_analytics.business_events_by_day (
    event_date, event_time, event_id, event_type, order_id,
    product_sku, quantity, amount, payment_method, status, metadata
) VALUES (
    '2026-10-08', '2026-10-08 03:00:00+0000',
    11111111-1111-1111-1111-111111111111, 'INVENTORY_RECEIVED', null,
    '1', 100, 0, null, 'COMPLETED',
    '{"batchCode":"1","source":"seed"}'
);

SELECT * FROM supermarket_analytics.business_events_by_day
WHERE event_date = '2026-10-08';
