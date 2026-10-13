package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.api.supermarket.dto.request.ProductRequest;
import com.api.supermarket.dto.request.StockAdjustmentRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.ProductResponse;
import com.api.supermarket.dto.response.StockMovementResponse;
import com.api.supermarket.entity.Category;
import com.api.supermarket.entity.Product;
import com.api.supermarket.entity.StockMovement;
import com.api.supermarket.entity.Supplier;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.CategoryRepository;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.StockMovementRepository;
import com.api.supermarket.repository.SupplierRepository;

@ExtendWith(MockitoExtension.class)
public class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private com.api.supermarket.service.InventoryBatchService inventoryBatchService;

    @InjectMocks
    private ProductServiceImpl productService;

    private ProductRequest createRequest() {
        ProductRequest request = new ProductRequest();
        request.setProductName("Sản phẩm A");
        request.setSkuCode("SKU123");
        request.setPrice(new BigDecimal("100000.00"));
        request.setStockQuantity(50);
        request.setReorderPoint(10);
        request.setExpiryDate(LocalDate.of(2026, 12, 31));
        request.setImageUrl("http://example.com/image.jpg");
        request.setCategoryId(1L);
        request.setSupplierId(1L);
        return request;
    }

    private Product createProduct() {
        Product product = new Product();
        product.setProductId(1L);
        product.setProductName("Sản phẩm A");
        product.setSkuCode("SKU123");
        product.setPrice(new BigDecimal("100000.00"));
        product.setStockQuantity(50);
        product.setReservedQuantity(5);
        product.setDamagedQuantity(0);
        product.setReorderPoint(10);
        product.setExpiryDate(LocalDate.of(2026, 12, 31));
        product.setImageUrl("http://example.com/image.jpg");
        product.setIsActive(true);
        product.setCategory(createCategory());
        product.setSupplier(createSupplier());
        product.setCreateAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        return product;
    }

    private Category createCategory() {
        Category category = new Category();
        category.setCategoryId(1L);
        category.setCategoryName("Thực phẩm");
        return category;
    }

    private Supplier createSupplier() {
        Supplier supplier = new Supplier();
        supplier.setSupplierId(1L);
        supplier.setSupplierName("Nhà cung cấp A");
        return supplier;
    }

    @Test
    void createProductShouldSucceed() {
        ProductRequest request = createRequest();
        Category category = createCategory();
        Supplier supplier = createSupplier();

        when(productRepository.existsBySkuCode(request.getSkuCode())).thenReturn(false);
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setProductId(1L);
            return product;
        });

        ProductResponse product = productService.createProduct(request);

        assertEquals(1L, product.getProductId());
        assertEquals("Sản phẩm A", product.getProductName());
        assertEquals("SKU123", product.getSkuCode());
        assertEquals(new BigDecimal("100000.00"), product.getPrice());
        assertEquals(50, product.getStockQuantity());
        assertEquals(10, product.getReorderPoint());
        assertEquals(LocalDate.of(2026, 12, 31), product.getExpiryDate());
        assertEquals(1L, product.getCategoryId());
        assertEquals("Thực phẩm", product.getCategoryName());
        assertEquals(1L, product.getSupplierId());
        assertEquals("Nhà cung cấp A", product.getSupplierName());
        assertTrue(product.getIsActive());
        assertEquals("http://example.com/image.jpg", product.getImageUrl());
        verify(productRepository).save(any(Product.class));
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void duplicateSkuShouldThrowBadRequest() {
        ProductRequest request = createRequest();
        when(productRepository.existsBySkuCode(request.getSkuCode())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> productService.createProduct(request));
        verify(productRepository, never()).save(any());
    }

    @Test
    void categoryNotFoundShouldThrowResourceNotFound() {
        ProductRequest request = createRequest();

        when(productRepository.existsBySkuCode(request.getSkuCode())).thenReturn(false);
        when(categoryRepository.findById(request.getCategoryId())).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productService.createProduct(request));

        assertEquals("Danh mục không tồn tại", exception.getMessage());
        verify(supplierRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void supplierNotFoundShouldThrowResourceNotFound() {
        ProductRequest request = createRequest();

        when(productRepository.existsBySkuCode(request.getSkuCode())).thenReturn(false);
        when(categoryRepository.findById(request.getCategoryId())).thenReturn(Optional.of(createCategory()));
        when(supplierRepository.findById(request.getSupplierId())).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productService.createProduct(request));

        assertEquals("Nhà cung cấp không tồn tại", exception.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    void productNotFoundShouldThrowResourceNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> productService.getProductById(99L));
    }

    @Test
    void updateWithoutIsActiveShouldKeepOldValue() {
        Product existingProduct = createProduct();
        existingProduct.setIsActive(false);

        ProductRequest request = createRequest();
        request.setIsActive(null);

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingProduct));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(createSupplier()));
        when(productRepository.save(existingProduct)).thenReturn(existingProduct);

        ProductResponse response = productService.updateProduct(1L, request);

        assertFalse(response.getIsActive());
        verify(productRepository).save(existingProduct);
    }

    @Test
    void updateProductShouldThrowBadRequestWhenStockQuantityChangesDirectly() {
        Product existingProduct = createProduct();
        ProductRequest request = createRequest();
        request.setStockQuantity(999);

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingProduct));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory()));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(createSupplier()));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> productService.updateProduct(1L, request));

        assertTrue(ex.getMessage().contains("stock-adjustments"));
    }

    @Test
    void mapToProductResponseShouldReturnCorrectData() {
        Product product = createProduct();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductById(1L);

        assertEquals(product.getProductId(), response.getProductId());
        assertEquals(product.getProductName(), response.getProductName());
        assertEquals(product.getPrice(), response.getPrice());
        assertEquals(product.getCategory().getCategoryName(), response.getCategoryName());
        assertEquals(product.getSupplier().getSupplierName(), response.getSupplierName());
        assertEquals(product.getCreateAt(), response.getCreateAt());
    }

    @Test
    void deleteProductShouldSucceedAsSoftDelete() {
        Product product = createProduct();
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        productService.deleteProduct(1L);

        assertFalse(product.getIsActive());
        verify(productRepository).save(product);
    }

    @Test
    void adjustStock_Increase_Success() {
        Product product = createProduct();
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setQuantity(10);
        request.setReason("Nhập thêm hàng kiểm kê");

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponse response = productService.adjustStock(1L, request);

        assertEquals(60, response.getStockQuantity());
        verify(stockMovementRepository).save(any(StockMovement.class));
        verify(productRepository).save(product);
    }

    @Test
    void adjustStock_Decrease_Success() {
        Product product = createProduct(); // stock=50, reserved=5
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setQuantity(-10);
        request.setReason("Hư hỏng bể vỡ");

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponse response = productService.adjustStock(1L, request);

        assertEquals(40, response.getStockQuantity());
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void adjustStock_ZeroQuantity_ThrowsBadRequest() {
        Product product = createProduct();
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setQuantity(0);

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> productService.adjustStock(1L, request));
        assertEquals("Số lượng điều chỉnh phải khác 0", ex.getMessage());
    }

    @Test
    void adjustStock_DecreaseBelowReserved_ThrowsBadRequest() {
        Product product = createProduct(); // stock=50, reserved=5
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setQuantity(-46); // 50 - 46 = 4 < 5 (reserved)

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> productService.adjustStock(1L, request));
        assertTrue(ex.getMessage().contains("không được âm hoặc thấp hơn số lượng đã giữ"));
    }

    @Test
    void getStockMovements_Success() {
        when(productRepository.existsById(1L)).thenReturn(true);
        StockMovement movement = new StockMovement();
        movement.setMovementId(10L);
        movement.setQuantity(5);
        movement.setMovementType("ADJUSTMENT");
        movement.setNote("Kiểm kê");
        movement.setPerformedBy("admin");
        movement.setCreatedAt(LocalDateTime.now());

        Page<StockMovement> page = new PageImpl<>(List.of(movement));
        when(stockMovementRepository.findByProduct_ProductIdOrderByMovementIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<StockMovementResponse> result = productService.getStockMovements(1L, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("ADJUSTMENT", result.getContent().get(0).movementType());
    }

    @Test
    void filterProducts_Success() {
        Product product = createProduct();
        Page<Product> productPage = new PageImpl<>(List.of(product));
        when(productRepository.filterProducts(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(productPage);

        PageResponse<ProductResponse> response = productService.filterProducts(
                0, 10, "productId", "asc", "Sản phẩm", 1L, 1L, true);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Sản phẩm A", response.getContent().get(0).getProductName());
    }
}
