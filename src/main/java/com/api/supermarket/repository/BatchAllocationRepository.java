package com.api.supermarket.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.api.supermarket.entity.BatchAllocation;

public interface BatchAllocationRepository extends JpaRepository<BatchAllocation, Long> {
    List<BatchAllocation> findByOrderItemIdOrderByIdAsc(Long orderItemId);
}
