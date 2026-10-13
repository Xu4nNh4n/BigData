package com.api.supermarket.service.Impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.supermarket.dto.response.TokenResponse;
import com.api.supermarket.entity.RefreshToken;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.UnauthorizedException;
import com.api.supermarket.repository.RefreshTokenRepository;
import com.api.supermarket.security.JwtService;
import com.api.supermarket.service.RefreshTokenService;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final@Value("${jwt.expiration}") long refreshTokenExpiration;
    private final int accessTokenExpiration;

    // SecureRandom dùng để tạo token có độ ngẫu nhiên đủ an toàn
    private final SecureRandom secureRandom = new SecureRandom();
    public RefreshTokenServiceImpl(
        RefreshTokenRepository refreshTokenRepository, 
        JwtService jwtService, 
        @Value("${jwt.refresh-expiration}") long refreshTokenExpiration, @
        Value("${jwt.expiration}") int accessTokenExpiration
    ){
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    @Override
    @Transactional
    public String createRefreshToken(User user){
        //Token gốc chỉ được trả về cho client, không được lưu trực tiếp vào database
        String rawToken = generateRawToken();

        //Database chỉ lưu SHA-256 hash của token
        String tokenHash = hashToken(rawToken);

        Instant now = Instant.now();

        //Tạo đối tượng RefreshToken và lưu vào database
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(now.plusMillis(refreshTokenExpiration));
        refreshToken.setRevoked(false);
        refreshToken.setCreateAt(now);
        refreshToken.setRevokedAt(null);

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Override
    @Transactional
    public TokenResponse refresh(String rawToken){
        String tokenHash = hashToken(rawToken);

        RefreshToken storedToken = refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new UnauthorizedException("Refresh token không hợp lệ"));

        //Token đã logout hoặc đã được sử dụng để refresh thì không dùng lại được
        if(Boolean.TRUE.equals(storedToken.getRevoked())){
            throw new UnauthorizedException("Refresh token đã bị thu hồi");
        }

        //isAfter(now) phải là true thì token mới còn hạn
        if(!storedToken.getExpiresAt().isAfter(Instant.now())){
            throw new UnauthorizedException("Refresh token đã hết hạn");
        }

        User user = storedToken.getUser();

        //Tài khoản bị khóa thì refresh token cũng không còn giá trị
        if(user == null || !Boolean.TRUE.equals(user.getIsActive())){
            throw new UnauthorizedException("Tài khoản không tồn tại hoặc bị khóa");
        }

        //Rotation: thu hồi refresh token cũ sau khi sử dụng
        storedToken.setRevoked(true);
        storedToken.setRevokedAt(Instant.now());

        //Tạo access token và refresh token mới.
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = createRefreshToken(user);
        return new TokenResponse(newAccessToken, newRefreshToken, "Bearer", accessTokenExpiration);
    }

    @Override
    @Transactional
    public void revoke(String rawToken){
        String tokenHash = hashToken(rawToken);

        RefreshToken storedToken = refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(() -> new UnauthorizedException("Refresh token không hợp lệ"));

            //Logout nên có tính idempotent:
            //Gọi là logout bằng token đã thu hồi không làm phát sinh lỗi dữ liệu
            if(Boolean.TRUE.equals(storedToken.getRevoked())){
                return;
            }
            storedToken.setRevoked(true);
            storedToken.setRevokedAt(Instant.now());
    }

    private String generateRawToken(){
        //32 bytes = 256 bit, đủ dài để tránh trùng lặp
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);

        //URL-safe giúp token gửi qua JSON/HTTP thuận tiện hơn.
        return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(randomBytes);
    }

    private String hashToken(String rawToken){
        if(rawToken == null || rawToken.isBlank()){
            throw new UnauthorizedException("Refresh token không được để trống");
        }
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hashBytes = digest.digest(
                rawToken.getBytes(StandardCharsets.UTF_8)
            );
            //SHA-256 dạng hex luôn dài 64 ký tự,
            //Khớp với token_hash VARCHAR(64).
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException ex){
            //SHA-256 luôn có trong Java, lỗi này là lỗi cấu hình hệ thống.
            throw new RuntimeException("Lỗi khi hash refresh token", ex);
        }
    
    }

    private String bytesToHex(byte[] bytes){
        StringBuilder result = new StringBuilder();

        for(byte value : bytes){
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

}
