package com.api.supermarket.repository;

import com.api.supermarket.entity.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long>{

    //Kiểm tra trùng email hoặc số điện thoại
    boolean existsBySupplierEmail(String email);
    boolean existsBySupplierPhone(String phone);
    
    @Query("""
            SELECT s FROM Supplier s
            WHERE (:keyword IS NULL OR LOWER(s.supplierName) LIKE LOWER(CONCAT('%' , :keyword, '%')))
            AND (:phone IS NULL OR s.supplierPhone = :phone)
            AND (:email IS NULL OR s.supplierEmail = :email)
            AND (:address IS NULL OR LOWER(s.supplierAddress) LIKE LOWER(CONCAT('%' , :address, '%')))
            AND (:active IS NULL OR s.isActive = :active)
            """)
    Page<Supplier> filterSuppliers(
        @Param("keyword") String keyword,
        @Param("phone") String phone,
        @Param("email") String email,
        @Param("address") String address,
        @Param("active") Boolean active,
        Pageable pageable
    );
            
} 
