package com.api.supermarket.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sales_order_return_items", uniqueConstraints = @UniqueConstraint(columnNames = {"return_id", "order_item_id"}))
public class SalesOrderReturnItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_item_id")
    private Long returnItemId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private SalesOrderReturn salesOrderReturn;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private SalesOrderItem orderItem;
    @Column(name = "batch_id")
    private Long batchId;
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    @Column(name = "restock", nullable = false)
    private boolean restock;
    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    public Long getReturnItemId() { return returnItemId; }
    public SalesOrderReturn getSalesOrderReturn() { return salesOrderReturn; }
    public void setSalesOrderReturn(SalesOrderReturn salesOrderReturn) { this.salesOrderReturn = salesOrderReturn; }
    public SalesOrderItem getOrderItem() { return orderItem; }
    public void setOrderItem(SalesOrderItem orderItem) { this.orderItem = orderItem; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public boolean isRestock() { return restock; }
    public void setRestock(boolean restock) { this.restock = restock; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
