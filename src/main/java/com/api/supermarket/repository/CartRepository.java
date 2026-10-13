package com.api.supermarket.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.api.supermarket.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.cartId = :id")
    Optional<Cart> findByIdForUpdate(@Param("id") Long id);
}
