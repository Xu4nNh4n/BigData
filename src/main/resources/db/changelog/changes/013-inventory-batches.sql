CREATE TABLE inventory_batches (
    batch_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    supplier_id BIGINT NULL,
    batch_code VARCHAR(100) NOT NULL UNIQUE,
    received_quantity INT NOT NULL,
    remaining_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL DEFAULT 0,
    damaged_quantity INT NOT NULL DEFAULT 0,
    unit_cost DECIMAL(18,2) NOT NULL,
    manufacturing_date DATE NULL,
    expiry_date DATE NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_by VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES products(product_id),
    CONSTRAINT fk_batch_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    INDEX idx_batch_product_fefo (product_id, expiry_date, received_at, batch_id)
);

CREATE TABLE batch_allocations (
    allocation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_item_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    returned_quantity INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_allocation_item_batch UNIQUE (order_item_id, batch_id),
    CONSTRAINT fk_allocation_item FOREIGN KEY (order_item_id) REFERENCES sales_order_items(order_item_id),
    CONSTRAINT fk_allocation_batch FOREIGN KEY (batch_id) REFERENCES inventory_batches(batch_id)
);

ALTER TABLE sales_order_return_items ADD CONSTRAINT fk_return_item_batch
    FOREIGN KEY (batch_id) REFERENCES inventory_batches(batch_id);

-- A migration balance is explicitly marked; zero cost means historical cost is unknown.
INSERT INTO inventory_batches (product_id, supplier_id, batch_code, received_quantity,
    remaining_quantity, reserved_quantity, damaged_quantity, unit_cost, expiry_date, received_by)
SELECT product_id, supplier_id, CONCAT('BASELINE-', product_id), stock_quantity,
    stock_quantity, reserved_quantity, damaged_quantity, 0, expiry_date, 'MIGRATION'
FROM products;

-- Carry forward only existing holds; never manufacture lot provenance for historical sales.
INSERT INTO batch_allocations (order_item_id, batch_id, quantity, status)
SELECT i.order_item_id, b.batch_id, i.quantity, 'RESERVED'
FROM sales_order_items i
JOIN sales_orders o ON o.order_id = i.order_id
JOIN inventory_batches b ON b.product_id = i.product_id AND b.batch_code = CONCAT('BASELINE-', i.product_id)
WHERE o.inventory_deducted = FALSE AND o.inventory_released = FALSE;
