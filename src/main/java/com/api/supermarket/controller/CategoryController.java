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

import com.api.supermarket.dto.request.CategoryRequest;
import com.api.supermarket.dto.response.CategoryResponse;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.service.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Category Management - Quản lý danh mục hàng hóa", description = "Product category categorization and management - Phân loại và quản lý các danh mục sản phẩm")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "Get all categories - Lấy danh sách danh mục", description = "Get category list with pagination, search by keyword, and active status - Lấy danh sách danh mục có phân trang, tìm kiếm và lọc trạng thái")
    public PageResponse<CategoryResponse> getAllCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "categoryId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Boolean active) {
        return categoryService.filterCategories(page, size, sortBy, sortDir, keyword, description, active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID - Lấy thông tin danh mục theo ID", description = "Get category details by category ID - Xem chi tiết thông tin danh mục theo mã ID")
    public CategoryResponse getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping
    @Operation(summary = "Create category - Tạo danh mục mới", description = "Add a new product category to the system - Tạo một danh mục hàng hóa mới trong hệ thống")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return categoryService.createCategory(request);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PutMapping("/{id}")
    @Operation(summary = "Update category - Cập nhật danh mục", description = "Update information of an existing category - Cập nhật thông tin của danh mục hiện có")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete category - Xóa danh mục", description = "Delete category if no products are attached - Xóa danh mục nếu không còn sản phẩm nào gắn kèm")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
