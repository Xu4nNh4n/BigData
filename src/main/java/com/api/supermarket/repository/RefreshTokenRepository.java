package com.api.supermarket.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.api.supermarket.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUser_UserIdAndRevokedFalse(Long userId);

    void deleteByExpiresAtBefore(Instant now);
}
