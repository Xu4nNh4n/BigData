package com.api.supermarket.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.request.RegisterCustomerRequest;
import com.api.supermarket.dto.response.CustomerResponse;
import com.api.supermarket.service.CustomerAccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Customer Account - Tài khoản khách hàng", description = "Customer self-registration and personal profile management - Đăng ký tài khoản và quản lý thông tin cá nhân của khách hàng")
public class CustomerAccountController {
    private final CustomerAccountService accountService;

    public CustomerAccountController(CustomerAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/api/auth/register")
    @Operation(summary = "Register customer account - Đăng ký tài khoản khách hàng", description = "Public API for customers to register an account with role ROLE_CUSTOMER and profile - Đăng ký tài khoản khách hàng mới kèm hồ sơ thành viên")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.register(request));
    }

}
