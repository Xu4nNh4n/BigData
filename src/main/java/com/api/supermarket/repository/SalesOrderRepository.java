package com.api.supermarket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.api.supermarket.entity.SalesOrder;

import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT so FROM SalesOrder so WHERE so.orderId = :id")
        Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

        @Query("SELECT so.orderId FROM SalesOrder so WHERE so.status = 'PENDING_PAYMENT' "
                + "AND so.paymentExpiresAt <= :now ORDER BY so.orderId")
        List<Long> findExpiredPaymentOrderIds(@Param("now") java.time.LocalDateTime now, Pageable pageable);

        @Query("SELECT so FROM SalesOrder so LEFT JOIN so.user creator LEFT JOIN so.customer c LEFT JOIN c.user cu "
                + "WHERE so.orderType = 'ONLINE' AND (creator.userName = :username OR cu.userName = :username)")
        Page<SalesOrder> findMyOnlineOrders(@Param("username") String username, Pageable pageable);

        Optional<SalesOrder> findByOrderCodeIgnoreCase(String orderCode);

        boolean existsByOrderCodeIgnoreCase(String orderCode);

        Page<SalesOrder> findByStatusIgnoreCase(String status, Pageable pageable);


        Page<SalesOrder> findByUser_UserNameIgnoreCase(String userName, Pageable pageable);

        Page<SalesOrder> findByOrderTypeIgnoreCase(String orderType, Pageable pageable);

        Page<SalesOrder> findByPaymentMethodIgnoreCase(String paymentMethod, Pageable pageable);

        @Query("""
                        SELECT so FROM SalesOrder so
                        LEFT JOIN so.customer c
                        LEFT JOIN so.user u
                        WHERE (:keyword IS NULL OR LOWER(so.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                            OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
                        AND (:status IS NULL OR so.status = :status)
                        AND (:orderType IS NULL OR so.orderType = :orderType)
                        AND (:paymentMethod IS NULL OR so.paymentMethod = :paymentMethod)
                        AND (:customerName IS NULL OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :customerName, '%')))
                        AND (:staffName IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :staffName, '%')))
                        """)
        Page<SalesOrder> filterSalesOrder(
                        @Param("keyword") String keyword,
                        @Param("status") String status,
                        @Param("orderType") String orderType,
                        @Param("paymentMethod") String paymentMethod,
                        @Param("customerName") String customerName,
                        @Param("staffName") String staffName,
                        Pageable pageable);
}
