package com.api.supermarket.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.api.supermarket.entity.SalesOrderReturnItem;

public interface SalesOrderReturnItemRepository extends JpaRepository<SalesOrderReturnItem, Long> {
    List<SalesOrderReturnItem> findBySalesOrderReturn_ReturnIdOrderByReturnItemIdAsc(Long returnId);
    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(i.quantity), 0) FROM SalesOrderReturnItem i "
            + "WHERE i.orderItem.orderItemId = :orderItemId")
    Integer sumReturnedByOrderItemId(@org.springframework.data.repository.query.Param("orderItemId") Long orderItemId);
}
