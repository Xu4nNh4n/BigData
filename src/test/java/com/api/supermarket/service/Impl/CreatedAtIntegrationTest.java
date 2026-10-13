package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.api.supermarket.dto.request.CategoryRequest;
import com.api.supermarket.dto.request.SupplierRequest;
import com.api.supermarket.dto.response.CategoryResponse;
import com.api.supermarket.dto.response.SupplierResponse;
import com.api.supermarket.service.CategoryService;
import com.api.supermarket.service.SupplierService;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class CreatedAtIntegrationTest {

    private final CategoryService categoryService;
    private final SupplierService supplierService;
    private final EntityManager entityManager;

    @Autowired
    CreatedAtIntegrationTest(
        CategoryService categoryService,
        SupplierService supplierService,
        EntityManager entityManager
    ) {
        this.categoryService = categoryService;
        this.supplierService = supplierService;
        this.entityManager = entityManager;
    }

    @Test
    void createCategoryWithoutCreateAtShouldUseDatabaseGeneratedValue() {
        CategoryRequest request = new CategoryRequest();
        request.setCategoryName("Test category " + System.nanoTime());
        request.setDescription("CreatedAt integration test");
        request.setIsActive(true);

        CategoryResponse createdCategory = categoryService.createCategory(request);

        entityManager.flush();
        entityManager.clear();

        CategoryResponse reloadedCategory = categoryService.getCategoryById(
            createdCategory.getCategoryId()
        );

        assertNotNull(reloadedCategory.getCreateAt());
    }

    @Test
    void createSupplierWithoutCreateAtShouldReturnCreatedAtInResponse() {
        SupplierRequest request = new SupplierRequest();
        request.setSupplierName("Test supplier " + System.nanoTime());
        request.setSupplierAddress("CreatedAt integration test");
        request.setIsActive(true);

        SupplierResponse createdSupplier = supplierService.createSupplier(request);

        entityManager.flush();
        entityManager.clear();

        SupplierResponse reloadedSupplier = supplierService.getSupplierbyId(
            createdSupplier.getSupplierId()
        );

        assertNotNull(reloadedSupplier.getCreateAt());
    }
}
