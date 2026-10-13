package com.api.supermarket.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id", nullable = false)
    private Long refreshTokenId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash; //lưu giá trị hash của refresh token, không lưu token gốc.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; //token thuộc người dùng nào.

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt; //thời điểm hết hạn.

    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false; //token đã bị thu hồi chưa.

    @Column(name = "created_at", nullable = false)
    private Instant createAt; //thời điểm tạo token, có thể dùng để kiểm tra thời gian sống của token.

    @Column(name = "revoked_at")
    private Instant revokedAt; //thời điểm logout hoặc thu hồi token.

    // Getters and Setters

    public Long getRefreshTokenId() {
        return refreshTokenId;
    }

    public void setRefreshTokenId(Long refreshTokenId) {
        this.refreshTokenId = refreshTokenId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getRevoked() {
        return revoked;
    }

    public void setRevoked(Boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getCreateAt() {
        return createAt;
    }

    public void setCreateAt(Instant createAt) {
        this.createAt = createAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }


}
