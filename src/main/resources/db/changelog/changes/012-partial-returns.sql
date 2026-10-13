ALTER TABLE sales_order_items ADD COLUMN returned_quantity INT NOT NULL DEFAULT 0;

-- Legacy full returns must not become eligible for another stock return.
UPDATE sales_order_items
SET returned_quantity = quantity
WHERE order_id IN (SELECT order_id FROM sales_orders WHERE status = 'RETURNED');

CREATE TABLE sales_order_returns (
    return_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    refund_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    refund_status VARCHAR(30) NOT NULL,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    refunded_at TIMESTAMP NULL,
    CONSTRAINT uk_return_request UNIQUE (request_key),
    CONSTRAINT fk_return_order FOREIGN KEY (order_id) REFERENCES sales_orders(order_id),
    INDEX idx_returns_order (order_id, return_id)
);

CREATE TABLE sales_order_return_items (
    return_item_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    batch_id BIGINT NULL,
    quantity INT NOT NULL,
    restock BOOLEAN NOT NULL,
    reason VARCHAR(255) NOT NULL,
    CONSTRAINT uk_return_order_item UNIQUE (return_id, order_item_id),
    CONSTRAINT fk_return_item_header FOREIGN KEY (return_id) REFERENCES sales_order_returns(return_id),
    CONSTRAINT fk_return_item_order_item FOREIGN KEY (order_item_id) REFERENCES sales_order_items(order_item_id)
);

ALTER TABLE payment_records ADD COLUMN return_id BIGINT NULL;
ALTER TABLE payment_records ADD CONSTRAINT uk_payment_return UNIQUE (return_id);
ALTER TABLE payment_records ADD CONSTRAINT fk_payment_return FOREIGN KEY (return_id) REFERENCES sales_order_returns(return_id);

-- Keep an order_id index when removing the old order/event uniqueness.
CREATE INDEX idx_payment_order_event ON payment_records(order_id, event_type);
