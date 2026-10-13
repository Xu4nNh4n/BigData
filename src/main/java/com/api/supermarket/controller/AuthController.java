package com.api.supermarket.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.request.LoginRequest;
import com.api.supermarket.dto.request.RefreshTokenRequest;
import com.api.supermarket.dto.response.AccountProfileResponse;
import com.api.supermarket.dto.request.UpdateProfileRequest;
import com.api.supermarket.dto.request.ChangePasswordRequest;
import com.api.supermarket.service.AccountProfileService;
import com.api.supermarket.dto.response.LoginResponse;
import com.api.supermarket.dto.response.TokenResponse;
import com.api.supermarket.service.AuthService;
import com.api.supermarket.service.RefreshTokenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication - Xác thực & Phân quyền", description = "APIs for login, token refresh, profile and logout - Các API đăng nhập, cấp lại token và thông tin phiên làm việc")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AccountProfileService profiles;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService, AccountProfileService profiles) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.profiles = profiles;
    }

    @PostMapping("/login")
    @Operation(summary = "Login - Đăng nhập", description = "Authenticate credentials and issue JWT access token and refresh token - Xác thực tài khoản và cấp phát access token cùng refresh token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info - Xem thông tin tài khoản hiện tại", description = "Retrieve profile details of the currently logged-in user - Lấy thông tin cá nhân của người dùng hiện đang đăng nhập")
    public AccountProfileResponse me(Authentication authentication) {
        return profiles.get(authentication.getName());
    }

    @PutMapping("/me")
    @Operation(summary = "Cập nhật thông tin tài khoản đang đăng nhập")
    public AccountProfileResponse updateMe(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(authentication.getName(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Đổi mật khẩu bằng mật khẩu hiện tại; thu hồi refresh token")
    public java.util.Map<String, String> changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        profiles.changePassword(authentication.getName(), request);
        return java.util.Map.of("message", "Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token - Làm mới access token", description = "Issue a new access token using a valid refresh token - Cấp phát access token mới thông qua refresh token hợp lệ")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return refreshTokenService.refresh(request.getRefreshToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout - Đăng xuất", description = "Revoke active refresh token to end user session - Thu hồi refresh token để hủy phiên đăng nhập")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
