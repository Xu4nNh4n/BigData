package com.api.supermarket.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "sales_order_returns")
public class SalesOrderReturn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id")
    private Long returnId;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "request_key", nullable = false, unique = true, length = 100)
    private String requestKey;
    @Column(name = "refund_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;
    @Column(name = "refund_status", nullable = false, length = 30)
    private String refundStatus;
    @Column(name = "created_by", length = 100)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    public Long getReturnId() { return returnId; }
    public void setReturnId(Long returnId) { this.returnId = returnId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
}
