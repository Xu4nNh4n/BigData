package com.api.supermarket.service;

import com.api.supermarket.dto.request.ChangePasswordRequest;
import com.api.supermarket.dto.request.UpdateProfileRequest;
import com.api.supermarket.dto.response.AccountProfileResponse;
import com.api.supermarket.entity.Customer;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.UnauthorizedException;
import com.api.supermarket.repository.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountProfileService {
    private final UserRepository users;
    private final CustomerRepository customers;
    private final RefreshTokenRepository tokens;
    private final PasswordEncoder encoder;

    public AccountProfileService(UserRepository users, CustomerRepository customers,
            RefreshTokenRepository tokens, PasswordEncoder encoder) {
        this.users = users;
        this.customers = customers;
        this.tokens = tokens;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public AccountProfileResponse get(String username) {
        User user = active(users.findByUserName(username).orElse(null));
        return response(user, customers.findByUser_UserId(user.getUserId()).orElse(null));
    }

    @Transactional
    public AccountProfileResponse update(String username, UpdateProfileRequest request) {
        User user = active(users.findByUserNameForUpdate(username).orElse(null));
        Customer customer = customers.findByUser_UserId(user.getUserId()).orElse(null);
        if ("CUSTOMER".equals(user.getRole().getRoleName()) && customer == null) {
            throw new BadRequestException("Tài khoản chưa có hồ sơ khách hàng; vui lòng liên hệ quản lý");
        }
        String email = nullable(request.email());
        if (email != null) email = email.toLowerCase(Locale.ROOT);
        String phone = nullable(request.phone());
        if ("CUSTOMER".equals(user.getRole().getRoleName()) && (email == null || phone == null)) {
            throw new BadRequestException("Tài khoản khách hàng cần email và số điện thoại");
        }
        if (email != null && users.existsByEmailIgnoreCaseAndUserIdNot(email, user.getUserId())) {
            throw new BadRequestException("Email đã được tài khoản khác sử dụng");
        }
        if (phone != null && users.existsByPhoneAndUserIdNot(phone, user.getUserId())) {
            throw new BadRequestException("Số điện thoại đã được tài khoản khác sử dụng");
        }
        if (customer != null) {
            if (email != null && customers.existsByEmailIgnoreCaseAndCustomerIdNot(email, customer.getCustomerId())) {
                throw new BadRequestException("Email đã được khách hàng khác sử dụng");
            }
            if (phone != null && customers.existsByPhoneAndCustomerIdNot(phone, customer.getCustomerId())) {
                throw new BadRequestException("Số điện thoại đã được khách hàng khác sử dụng");
            }
            customer.setFullName(request.fullName().trim());
            customer.setEmail(email);
            customer.setPhone(phone);
            customer.setAddress(nullable(request.address()));
        }
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        return response(user, customer);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = active(users.findByUserNameForUpdate(username).orElse(null));
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu hiện tại không đúng");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Xác nhận mật khẩu mới không khớp");
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Mật khẩu mới vượt giới hạn 72 byte; vui lòng nhập ngắn hơn");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        Instant now = Instant.now();
        tokens.findByUser_UserIdAndRevokedFalse(user.getUserId()).forEach(token -> {
            token.setRevoked(true);
            token.setRevokedAt(now);
        });
    }

    private User active(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Tài khoản không tồn tại hoặc đã bị khóa");
        }
        return user;
    }

    private AccountProfileResponse response(User user, Customer customer) {
        return new AccountProfileResponse(user.getUserId(), user.getUserName(), user.getFullName(),
                user.getEmail(), user.getPhone(), user.getRole().getRoleName(),
                customer == null ? null : customer.getAddress());
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
