package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.api.supermarket.dto.request.SupplierRequest;
import com.api.supermarket.dto.response.SupplierResponse;
import com.api.supermarket.entity.Supplier;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.SupplierRepository;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    @Test
    void createSupplierShouldSucceed() {
        // Arrange = là chuẩn bị dữ liệu và môi trường test trước khi thực hiện hành động cần test.
        SupplierRequest request = createRequest();

        when(supplierRepository.existsBySupplierEmail(request.getSupplierEmail())).thenReturn(false); // Giả lập rằng email chưa tồn tại
        when(supplierRepository.existsBySupplierPhone(request.getSupplierPhone())).thenReturn(false); // Giả lập rằng số điện thoại chưa tồn tại
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier supplier = invocation.getArgument(0);
            supplier.setSupplierId(1L); // Giả lập ID được gán sau khi lưu vào database
            return supplier;
        });
        // Act
        SupplierResponse response = supplierService.createSupplier(request);
        // Assert
        assertEquals(1L, response.getSupplierId());
        assertEquals("Nhà cung cấp A", response.getSupplierName());
        assertTrue(response.getIsActive());
        verify(supplierRepository).save(any(Supplier.class));
    }

    @Test
    void duplicateEmailShouldThrowBadRequest() {
        // Arrange
        SupplierRequest request = createRequest();
        when(supplierRepository.existsBySupplierEmail(request.getSupplierEmail())).thenReturn(true);
        // Act và Assert
        assertThrows(BadRequestException.class, () -> supplierService.createSupplier(request));
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void duplicatePhoneShouldThrowBadRequest() {
        // Arrange
        SupplierRequest request = createRequest();
        when(supplierRepository.existsBySupplierPhone(request.getSupplierPhone())).thenReturn(true);

        // Act và Assert
        assertThrows(BadRequestException.class, () -> supplierService.createSupplier(request));
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void supplierNotFoundShouldThrowResourceNotFound() {
        // Arrange
        when(supplierRepository.findById(99L)).thenReturn(Optional.empty());

        // Act và Assert
        assertThrows(
            ResourceNotFoundException.class,
            () -> supplierService.getSupplierbyId(99L)
        );
    }

    @Test
    void deleteSupplierHavingProductsShouldThrowBadRequest() {
        // Arrange
        when(productRepository.existsBySupplier_SupplierId(1L)).thenReturn(true);

        // Act và Assert
        assertThrows(BadRequestException.class, () -> supplierService.deleteSupplier(1L));
        verify(supplierRepository, never()).findById(any());
        verify(supplierRepository, never()).delete(any());
    }

    @Test
    void updateWithoutIsActiveShouldKeepOldValue() {
        // Arrange
        Supplier existingSupplier = createSupplier();
        existingSupplier.setIsActive(false);

        SupplierRequest request = createRequest();
        request.setIsActive(null);

        when(supplierRepository.findById(1L))
            .thenReturn(Optional.of(existingSupplier));
        when(supplierRepository.save(existingSupplier))
            .thenReturn(existingSupplier);

        // Act
        SupplierResponse response = supplierService.updateSupplier(1L, request);
        
        // Assert
        assertFalse(response.getIsActive());
        verify(supplierRepository).save(existingSupplier);
    }

    @Test
    void mapToSupplierResponseShouldReturnCorrectData() {
        // Arrange
        Supplier supplier = createSupplier();
        when(supplierRepository.findById(1L))
            .thenReturn(Optional.of(supplier));

        // Act
        SupplierResponse response = supplierService.getSupplierbyId(1L);

        // Assert
        assertEquals(supplier.getSupplierId(), response.getSupplierId());
        assertEquals(supplier.getSupplierName(), response.getSupplierName());
        assertEquals(supplier.getSupplierPhone(), response.getSupplierPhone());
        assertEquals(supplier.getSupplierEmail(), response.getSupplierEmail());
        assertEquals(supplier.getSupplierAddress(), response.getSupplierAddress());
        assertEquals(supplier.getIsActive(), response.getIsActive());
        assertEquals(supplier.getCreateAt(), response.getCreateAt());
    }

    private SupplierRequest createRequest() {
        SupplierRequest request = new SupplierRequest();
        request.setSupplierName("Nhà cung cấp A");
        request.setSupplierEmail("supplier@example.com");
        request.setSupplierPhone("0900000001");
        request.setSupplierAddress("Cần Thơ");
        request.setIsActive(null);
        return request;
    }

    private Supplier createSupplier() {
        Supplier supplier = new Supplier();
        supplier.setSupplierId(1L);
        supplier.setSupplierName("Nhà cung cấp A");
        supplier.setSupplierEmail("supplier@example.com");
        supplier.setSupplierPhone("0900000001");
        supplier.setSupplierAddress("Cần Thơ");
        supplier.setIsActive(true);
        supplier.setCreateAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        return supplier;
    }
}
