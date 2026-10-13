package com.api.supermarket.repository;

import com.api.supermarket.entity.Customer;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

        Optional<Customer> findByUser_UserId(Long userId);

        // 1. Phục vụ tìm chính xác & kiểm tra trùng lập (Validation / Nghiệp vụ bán
        // hàng)
        Optional<Customer> findByEmailIgnoreCase(String email);

        // 2. Kiểm tra tồn tại của Phone hoặc Email
        boolean existsByPhone(String phone);

        boolean existsByEmailIgnoreCase(String email);
        boolean existsByEmailIgnoreCaseAndCustomerIdNot(String email, Long customerId);
        boolean existsByPhoneAndCustomerIdNot(String phone, Long customerId);

        // 3.

        @Query("""
                        SELECT c FROM Customer c
                        WHERE (:keyword IS NULL OR LOWER(c.fullName) LIKE LOWER(CONCAT('%',:keyword,'%')))
                        AND (:phone IS NULL OR c.phone = :phone)
                        AND (:email IS NULL OR LOWER(c.email) = LOWER(:email))
                        AND (:address IS NULL OR LOWER(c.address) LIKE LOWER(CONCAT('%',:address,'%')))
                        AND (:active IS NULL OR c.isActive = :active)
                        """)
        Page<Customer> filterCustomers(
                        @Param("keyword") String keyword,
                        @Param("phone") String phone,
                        @Param("email") String email,
                        @Param("address") String address,
                        @Param("active") Boolean active,
                        Pageable pageable);
}
