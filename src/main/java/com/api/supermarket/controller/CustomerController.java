package com.api.supermarket.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.request.CustomerRequest;
import com.api.supermarket.dto.response.CustomerResponse;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.service.CustomerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customer Management - Quản lý khách hàng", description = "Customer profiles and phone lookup for POS/online orders - Quản lý hồ sơ khách hàng và tra cứu tại quầy/online")
public class CustomerController {

    private final CustomerService customerService;
    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Create customer - Tạo khách hàng mới", description = "Register a customer profile for POS and online orders - Tạo hồ sơ khách hàng cho POS và đặt hàng online")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
        log.info("Nhận yêu cầu tạo khách hàng mới: {}", request.getFullName());
        CustomerResponse response = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Update customer - Cập nhật thông tin khách hàng", description = "Update customer contact info, address or status - Cập nhật thông tin liên hệ, địa chỉ hoặc trạng thái khách hàng")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        log.info("Cập nhật thông tin khách hàng ID = {}", id);
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete customer - Vô hiệu hóa khách hàng", description = "Soft delete / deactivate a customer account - Xóa mềm (chuyển trạng thái ngừng hoạt động) của khách hàng")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        log.info("Xóa khách hàng ID = {}", id);
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Filter customers - Lọc và tìm kiếm danh sách khách hàng", description = "Paginate and search customers by keyword, phone, email, address, or active state - Lọc và tìm kiếm khách hàng có phân trang")
    public ResponseEntity<PageResponse<CustomerResponse>> filterCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "customerId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) Boolean active) {
        log.debug("Lọc danh sách khách hàng, page={}, size={}, keyword={}", page, size, keyword);
        PageResponse<CustomerResponse> response = customerService.filterCustomers(
                page, size, sortBy, sortDir, keyword, phone, email, address, active);
        return ResponseEntity.ok(response);
    }
}
