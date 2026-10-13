package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.api.supermarket.dto.response.TokenResponse;
import com.api.supermarket.entity.RefreshToken;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.UnauthorizedException;
import com.api.supermarket.repository.RefreshTokenRepository;
import com.api.supermarket.security.JwtService;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    private RefreshTokenServiceImpl refreshTokenService;

    private final long refreshTokenExpiration = 604800000L; // 7 days
    private final int accessTokenExpiration = 900000; // 15 mins

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(
                refreshTokenRepository,
                jwtService,
                refreshTokenExpiration,
                accessTokenExpiration);
    }

    @Test
    void createRefreshToken_Success() {
        User user = new User();
        user.setUserId(1L);

        String rawToken = refreshTokenService.createRefreshToken(user);

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refresh_Success_RotatesTokens() {
        String rawToken = "sample-raw-refresh-token";

        User user = new User();
        user.setUserId(1L);
        user.setIsActive(true);

        RefreshToken storedToken = new RefreshToken();
        storedToken.setUser(user);
        storedToken.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        storedToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(jwtService.generateToken(user)).thenReturn("newAccessToken");

        TokenResponse response = refreshTokenService.refresh(rawToken);

        assertNotNull(response);
        assertEquals("newAccessToken", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(accessTokenExpiration, response.getAccessTokenExpiresIn());
        assertTrue(storedToken.getRevoked());
        assertNotNull(storedToken.getRevokedAt());
    }

    @Test
    void refresh_RevokedToken_ThrowsUnauthorized() {
        String rawToken = "revoked-token";

        RefreshToken storedToken = new RefreshToken();
        storedToken.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> refreshTokenService.refresh(rawToken));
        assertEquals("Refresh token đã bị thu hồi", ex.getMessage());
    }

    @Test
    void refresh_ExpiredToken_ThrowsUnauthorized() {
        String rawToken = "expired-token";

        RefreshToken storedToken = new RefreshToken();
        storedToken.setRevoked(false);
        storedToken.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> refreshTokenService.refresh(rawToken));
        assertEquals("Refresh token đã hết hạn", ex.getMessage());
    }

    @Test
    void refresh_InactiveUser_ThrowsUnauthorized() {
        String rawToken = "valid-token";

        User user = new User();
        user.setUserId(1L);
        user.setIsActive(false);

        RefreshToken storedToken = new RefreshToken();
        storedToken.setRevoked(false);
        storedToken.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        storedToken.setUser(user);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> refreshTokenService.refresh(rawToken));
        assertEquals("Tài khoản không tồn tại hoặc bị khóa", ex.getMessage());
    }

    @Test
    void revoke_Success() {
        String rawToken = "token-to-revoke";

        RefreshToken storedToken = new RefreshToken();
        storedToken.setRevoked(false);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        refreshTokenService.revoke(rawToken);

        assertTrue(storedToken.getRevoked());
        assertNotNull(storedToken.getRevokedAt());
    }
}
