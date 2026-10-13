CREATE TABLE data_sync_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(150) NOT NULL,
    destination VARCHAR(30) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    delivered_at DATETIME(3) NULL,
    last_error VARCHAR(255) NULL,
    CONSTRAINT uk_data_sync_event_key UNIQUE (event_key),
    INDEX idx_data_sync_pending (status, next_attempt_at, id)
);
