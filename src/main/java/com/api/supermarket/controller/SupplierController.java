package com.api.supermarket.controller;

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

import com.api.supermarket.dto.request.SupplierRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.SupplierResponse;
import com.api.supermarket.service.SupplierService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/suppliers")
@Tag(name = "Supplier Management - Quản lý nhà cung cấp", description = "Vendor and supplier information management - Quản lý đối tác và nhà cung cấp hàng hóa cho siêu thị")
public class SupplierController {
    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @Operation(summary = "Get all suppliers - Lấy danh sách nhà cung cấp", description = "Paginate and filter suppliers by name, phone, email, address, or active state - Lấy danh sách nhà cung cấp có phân trang và bộ lọc")
    public PageResponse<SupplierResponse> getAllSuppliers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "supplierId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) Boolean active) {
        return supplierService.filterSuppliers(page, size, sortBy, sortDir, keyword, phone, email, address, active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get supplier by ID - Lấy thông tin nhà cung cấp theo ID", description = "Retrieve full supplier details by ID - Xem thông tin chi tiết nhà cung cấp theo mã ID")
    public SupplierResponse getSupplierbyId(@PathVariable Long id) {
        return supplierService.getSupplierbyId(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @PostMapping
    @Operation(summary = "Create supplier - Thêm nhà cung cấp mới", description = "Add a new vendor/supplier to the system - Thêm hồ sơ nhà cung cấp mới vào hệ thống")
    public SupplierResponse createSupplier(@Valid @RequestBody SupplierRequest request) {
        return supplierService.createSupplier(request);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @PutMapping("/{id}")
    @Operation(summary = "Update supplier - Cập nhật nhà cung cấp", description = "Update supplier contact information, address, and status - Cập nhật thông tin liên hệ, địa chỉ và trạng thái của nhà cung cấp")
    public SupplierResponse updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.updateSupplier(id, request);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'WAREHOUSE')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete supplier - Vô hiệu hóa nhà cung cấp", description = "Soft delete / deactivate a supplier - Xóa mềm (chuyển sang ngừng hợp tác) nhà cung cấp")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }
}
