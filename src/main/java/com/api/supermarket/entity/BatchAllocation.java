package com.api.supermarket.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "batch_allocations")
public class BatchAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id") public Long id;
    @Column(name = "order_item_id", nullable = false) public Long orderItemId;
    @Column(name = "batch_id", nullable = false) public Long batchId;
    @Column(name = "quantity", nullable = false) public int quantity;
    @Column(name = "returned_quantity", nullable = false) public int returned;
    @Column(name = "status", nullable = false, length = 20) public String status;
}
