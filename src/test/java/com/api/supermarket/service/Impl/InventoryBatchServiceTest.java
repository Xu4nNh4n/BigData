package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.api.supermarket.dto.request.ReceiveBatchRequest;
import com.api.supermarket.dto.request.StockAdjustmentRequest;
import com.api.supermarket.dto.response.BatchAllocationResponse;
import com.api.supermarket.entity.BatchAllocation;
import com.api.supermarket.entity.InventoryBatch;
import com.api.supermarket.entity.Product;
import com.api.supermarket.entity.SalesOrderItem;
import com.api.supermarket.entity.StockMovement;
import com.api.supermarket.entity.Supplier;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.InsufficientStockException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.BatchAllocationRepository;
import com.api.supermarket.repository.InventoryBatchRepository;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.StockMovementRepository;
import com.api.supermarket.repository.SupplierRepository;
import com.api.supermarket.service.InventoryBatchService;

@ExtendWith(MockitoExtension.class)
public class InventoryBatchServiceTest {

    @Mock
    private InventoryBatchRepository batches;

    @Mock
    private BatchAllocationRepository allocations;

    @Mock
    private ProductRepository products;

    @Mock
    private SupplierRepository suppliers;

    @Mock
    private StockMovementRepository movements;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private InventoryBatchService service;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Product createProduct(Long id, int stock, int reserved) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductName("Sữa tươi");
        p.setStockQuantity(stock);
        p.setReservedQuantity(reserved);
        p.setDamagedQuantity(0);
        p.setIsActive(true);
        p.setExpiryDate(today.plusDays(30));
        Supplier s = new Supplier();
        s.setSupplierId(10L);
        s.setIsActive(true);
        p.setSupplier(s);
        return p;
    }

    private InventoryBatch createBatch(Long id, Long productId, String code, int remaining, int reserved, LocalDate expiry) {
        InventoryBatch b = new InventoryBatch();
        b.id = id;
        b.productId = productId;
        b.supplierId = 10L;
        b.code = code;
        b.received = remaining + reserved;
        b.remaining = remaining;
        b.reserved = reserved;
        b.damaged = 0;
        b.unitCost = new BigDecimal("25000.00");
        b.expiryDate = expiry;
        b.receivedAt = LocalDateTime.now();
        b.receivedBy = "admin";
        b.status = "ACTIVE";
        return b;
    }

    @Test
    @DisplayName("receive: Nhập lô mới thành công và tăng số lượng tồn kho sản phẩm")
    void receive_NewBatch_Success() {
        Product product = createProduct(1L, 20, 0);
        Supplier supplier = product.getSupplier();

        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(batches.findByCode("BATCH-001")).thenReturn(Optional.empty());
        when(suppliers.findById(10L)).thenReturn(Optional.of(supplier));
        when(batches.save(any(InventoryBatch.class))).thenAnswer(i -> {
            InventoryBatch b = i.getArgument(0);
            b.id = 100L;
            return b;
        });
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("warehouse_staff");

        ReceiveBatchRequest req = new ReceiveBatchRequest(1L, 10L, "BATCH-001", 30,
                new BigDecimal("25000.00"), today.minusDays(2), today.plusDays(60));

        InventoryBatch result = service.receive(req);

        assertNotNull(result);
        assertEquals(100L, result.id);
        assertEquals(50, product.getStockQuantity()); // 20 + 30
        verify(products).save(product);
        verify(movements).save(any(StockMovement.class));
        verify(batches).save(any(InventoryBatch.class));
    }

    @Test
    @DisplayName("receive: Nhập lại đúng thông tin lô đã có thì trả về lô cũ (idempotent)")
    void receive_IdempotentExistingBatch_Success() {
        Product product = createProduct(1L, 20, 0);
        InventoryBatch existing = createBatch(100L, 1L, "BATCH-001", 30, 0, today.plusDays(60));
        existing.received = 30;
        existing.manufacturingDate = today.minusDays(2);

        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(batches.findByCode("BATCH-001")).thenReturn(Optional.of(existing));

        ReceiveBatchRequest req = new ReceiveBatchRequest(1L, 10L, "BATCH-001", 30,
                new BigDecimal("25000.00"), today.minusDays(2), today.plusDays(60));

        InventoryBatch result = service.receive(req);

        assertNotNull(result);
        assertEquals(100L, result.id);
        verify(products, never()).save(any());
        verify(movements, never()).save(any());
    }

    @Test
    @DisplayName("receive: Trùng mã lô nhưng khác số lượng ném ngoại lệ BadRequest")
    void receive_ConflictingExistingBatch_ThrowsBadRequest() {
        Product product = createProduct(1L, 20, 0);
        InventoryBatch existing = createBatch(100L, 1L, "BATCH-001", 30, 0, today.plusDays(60));
        existing.received = 30;

        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(batches.findByCode("BATCH-001")).thenReturn(Optional.of(existing));

        ReceiveBatchRequest req = new ReceiveBatchRequest(1L, 10L, "BATCH-001", 50, // Khác số lượng
                new BigDecimal("25000.00"), today.minusDays(2), today.plusDays(60));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.receive(req));
        assertEquals("Mã lô đã được nhập với nội dung khác", ex.getMessage());
    }

    @Test
    @DisplayName("receive: Ném ngoại lệ ResourceNotFound khi không tìm thấy sản phẩm")
    void receive_ProductNotFound_ThrowsResourceNotFound() {
        when(products.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        ReceiveBatchRequest req = new ReceiveBatchRequest(999L, 10L, "BATCH-999", 10,
                new BigDecimal("10000.00"), null, null);

        assertThrows(ResourceNotFoundException.class, () -> service.receive(req));
    }

    @Test
    @DisplayName("receive: Ném ngoại lệ khi sản phẩm đã ngừng kinh doanh")
    void receive_ProductInactive_ThrowsBadRequest() {
        Product product = createProduct(1L, 10, 0);
        product.setIsActive(false);

        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(batches.findByCode("BATCH-001")).thenReturn(Optional.empty());

        ReceiveBatchRequest req = new ReceiveBatchRequest(1L, 10L, "BATCH-001", 10,
                new BigDecimal("10000.00"), null, today.plusDays(10));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.receive(req));
        assertEquals("Sản phẩm đã ngừng kinh doanh", ex.getMessage());
    }

    @Test
    @DisplayName("receive: Ném ngoại lệ khi ngày sản xuất ở tương lai")
    void receive_ManufacturingDateInFuture_ThrowsBadRequest() {
        Product product = createProduct(1L, 10, 0);

        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(batches.findByCode("BATCH-001")).thenReturn(Optional.empty());

        ReceiveBatchRequest req = new ReceiveBatchRequest(1L, 10L, "BATCH-001", 10,
                new BigDecimal("10000.00"), today.plusDays(5), today.plusDays(30));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.receive(req));
        assertEquals("Ngày sản xuất không được ở tương lai", ex.getMessage());
    }

    @Test
    @DisplayName("usableQuantity: Trả về tổng lượng khả dụng còn hạn của sản phẩm")
    void usableQuantity_Success() {
        when(batches.usableQuantity(eq(1L), any(LocalDate.class))).thenReturn(45L);

        int quantity = service.usableQuantity(1L);

        assertEquals(45, quantity);
    }

    @Test
    @DisplayName("getAllocations: Lấy danh sách thông tin lô đã cấp phát cho món hàng")
    void getAllocations_Success() {
        BatchAllocation allocation = new BatchAllocation();
        allocation.id = 1L;
        allocation.orderItemId = 10L;
        allocation.batchId = 100L;
        allocation.quantity = 4;
        allocation.returned = 0;
        allocation.status = "RESERVED";

        InventoryBatch batch = createBatch(100L, 1L, "BATCH-A", 10, 4, today.plusDays(30));

        when(allocations.findByOrderItemIdOrderByIdAsc(10L)).thenReturn(List.of(allocation));
        when(batches.findAllById(List.of(100L))).thenReturn(List.of(batch));

        List<BatchAllocationResponse> result = service.getAllocations(10L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BATCH-A", result.get(0).batchCode());
        assertEquals(4, result.get(0).quantity());
    }

    @Test
    @DisplayName("list: Lấy danh sách lô theo sản phẩm thành công")
    void list_Success() {
        InventoryBatch b1 = createBatch(1L, 10L, "B1", 5, 0, today.plusDays(10));
        InventoryBatch b2 = createBatch(2L, 10L, "B2", 15, 0, today.plusDays(20));

        when(products.existsById(10L)).thenReturn(true);
        when(batches.findByProductIdOrderByIdAsc(10L)).thenReturn(List.of(b1, b2));

        List<InventoryBatch> result = service.list(10L);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("list: Ném ngoại lệ ResourceNotFound khi sản phẩm không tồn tại")
    void list_ProductNotFound_ThrowsResourceNotFound() {
        when(products.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.list(999L));
    }

    @Test
    @DisplayName("adjustBatch: Giảm tồn kho lô và sản phẩm thành công")
    void adjustBatch_Success() {
        InventoryBatch batch = createBatch(100L, 1L, "BATCH-ADJ", 15, 2, today.plusDays(30));
        Product product = createProduct(1L, 20, 2);

        when(batches.findById(100L)).thenReturn(Optional.of(batch));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch));
        when(batches.save(batch)).thenReturn(batch);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin");

        StockAdjustmentRequest req = new StockAdjustmentRequest();
        req.setQuantity(-3);
        req.setReason("Hư hỏng trong quá trình vận chuyển nội bộ");

        InventoryBatch result = service.adjustBatch(100L, req);

        assertNotNull(result);
        assertEquals(12, batch.remaining); // 15 - 3
        assertEquals(17, product.getStockQuantity()); // 20 - 3
        verify(products).save(product);
        verify(movements).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("adjustBatch: Ném ngoại lệ khi số lượng điều chỉnh >= 0")
    void adjustBatch_PositiveQuantity_ThrowsBadRequest() {
        StockAdjustmentRequest req = new StockAdjustmentRequest();
        req.setQuantity(5); // Tăng tồn

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.adjustBatch(100L, req));
        assertEquals("Điều chỉnh lô chỉ giảm tồn; tăng tồn dùng nhập lô mới", ex.getMessage());
    }

    @Test
    @DisplayName("adjustBatch: Ném ngoại lệ khi giảm vượt quá số lượng chưa giữ của lô")
    void adjustBatch_ExceedsAvailable_ThrowsBadRequest() {
        InventoryBatch batch = createBatch(100L, 1L, "BATCH-ADJ", 5, 3, today.plusDays(30)); // unreserved = 2
        Product product = createProduct(1L, 10, 3);

        when(batches.findById(100L)).thenReturn(Optional.of(batch));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch));

        StockAdjustmentRequest req = new StockAdjustmentRequest();
        req.setQuantity(-4); // -4 > 2 unreserved
        req.setReason("Hao hụt");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.adjustBatch(100L, req));
        assertEquals("Không được giảm quá lượng chưa giữ của lô", ex.getMessage());
    }

    @Test
    @DisplayName("opening: Tạo lô khởi đầu khi sản phẩm có tồn kho > 0")
    void opening_StockPositive_CreatesOpeningBatch() {
        Product product = createProduct(1L, 25, 0);

        service.opening(product);

        verify(batches).save(any(InventoryBatch.class));
    }

    @Test
    @DisplayName("opening: Không tạo lô khi sản phẩm có tồn kho bằng 0")
    void opening_StockZero_DoesNothing() {
        Product product = createProduct(1L, 0, 0);

        service.opening(product);

        verify(batches, never()).save(any());
    }

    @Test
    @DisplayName("allocate: Cấp phát lô giữ hàng (reserve=true) thành công theo FEFO")
    void allocate_ReserveTrue_Success() {
        Product product = createProduct(1L, 20, 0);
        InventoryBatch batch = createBatch(100L, 1L, "B-001", 10, 0, today.plusDays(15));

        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(50L);
        item.setProduct(product);
        item.setQuantity(4);

        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch));

        service.allocate(item, true);

        assertEquals(4, batch.reserved);
        assertEquals(10, batch.remaining);
        verify(allocations).save(any(BatchAllocation.class));
        verify(batches).saveAll(anyList());
    }

    @Test
    @DisplayName("allocate: Cấp phát trừ trực tiếp (reserve=false) thành công")
    void allocate_ReserveFalse_Success() {
        Product product = createProduct(1L, 20, 0);
        InventoryBatch batch = createBatch(100L, 1L, "B-001", 10, 0, today.plusDays(15));

        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(50L);
        item.setProduct(product);
        item.setQuantity(4);

        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch));

        service.allocate(item, false);

        assertEquals(0, batch.reserved);
        assertEquals(6, batch.remaining); // 10 - 4
        verify(allocations).save(any(BatchAllocation.class));
    }

    @Test
    @DisplayName("allocate: Ném ngoại lệ InsufficientStock khi tồn kho lô không đủ")
    void allocate_InsufficientStock_ThrowsInsufficientStock() {
        Product product = createProduct(1L, 20, 0);
        InventoryBatch batch = createBatch(100L, 1L, "B-001", 3, 0, today.plusDays(15)); // Chỉ có 3

        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(50L);
        item.setProduct(product);
        item.setQuantity(5); // Yêu cầu 5

        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch));

        assertThrows(InsufficientStockException.class, () -> service.allocate(item, true));
    }

    @Test
    @DisplayName("consumeReserved: Xuất kho thành công lô đã giữ trước đó")
    void consumeReserved_Success() {
        Product product = createProduct(1L, 20, 4);
        InventoryBatch batch = createBatch(100L, 1L, "B-001", 10, 4, today.plusDays(15));

        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(50L);
        item.setProduct(product);
        item.setQuantity(4);

        BatchAllocation allocation = new BatchAllocation();
        allocation.orderItemId = 50L;
        allocation.batchId = 100L;
        allocation.quantity = 4;
        allocation.status = "RESERVED";

        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch));
        when(allocations.findByOrderItemIdOrderByIdAsc(50L)).thenReturn(List.of(allocation));

        service.consumeReserved(item);

        assertEquals(0, batch.reserved); // 4 - 4
        assertEquals(6, batch.remaining); // 10 - 4
        assertEquals("CONSUMED", allocation.status);
        verify(batches).saveAll(any());
        verify(allocations).saveAll(any());
    }

    @Test
    @DisplayName("releaseReserved: Giải phóng tồn kho giữ thành công")
    void releaseReserved_Success() {
        Product product = createProduct(1L, 20, 4);
        InventoryBatch batch = createBatch(100L, 1L, "B-001", 10, 4, today.plusDays(15));

        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(50L);
        item.setProduct(product);
        item.setQuantity(4);

        BatchAllocation allocation = new BatchAllocation();
        allocation.orderItemId = 50L;
        allocation.batchId = 100L;
        allocation.quantity = 4;
        allocation.status = "RESERVED";

        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch));
        when(allocations.findByOrderItemIdOrderByIdAsc(50L)).thenReturn(List.of(allocation));

        service.releaseReserved(item);

        assertEquals(0, batch.reserved);
        assertEquals(10, batch.remaining);
        assertEquals("RELEASED", allocation.status);
    }

    @Test
    @DisplayName("receiveReturn: Nhận trả hàng vào kho (restock=true) thành công")
    void receiveReturn_RestockTrue_Success() {
        Product product = createProduct(1L, 20, 0);
        InventoryBatch batch = createBatch(100L, 1L, "B-001", 6, 0, today.plusDays(15));

        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(50L);
        item.setProduct(product);
        item.setQuantity(4);

        BatchAllocation allocation = new BatchAllocation();
        allocation.orderItemId = 50L;
        allocation.batchId = 100L;
        allocation.quantity = 4;
        allocation.returned = 0;
        allocation.status = "CONSUMED";

        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch));
        when(allocations.findByOrderItemIdOrderByIdAsc(50L)).thenReturn(List.of(allocation));

        service.receiveReturn(item, 2, true, 100L);

        assertEquals(8, batch.remaining); // 6 + 2
        assertEquals(2, allocation.returned);
        verify(batches).saveAll(any());
    }

    @Test
    @DisplayName("receiveReturn: Nhận trả hàng hư hỏng (restock=false) thành công")
    void receiveReturn_Damaged_Success() {
        Product product = createProduct(1L, 20, 0);
        InventoryBatch batch = createBatch(100L, 1L, "B-001", 6, 0, today.plusDays(15));

        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(50L);
        item.setProduct(product);
        item.setQuantity(4);

        BatchAllocation allocation = new BatchAllocation();
        allocation.orderItemId = 50L;
        allocation.batchId = 100L;
        allocation.quantity = 4;
        allocation.returned = 0;
        allocation.status = "CONSUMED";

        when(batches.lockProductBatches(1L)).thenReturn(List.of(batch));
        when(allocations.findByOrderItemIdOrderByIdAsc(50L)).thenReturn(List.of(allocation));

        service.receiveReturn(item, 2, false, 100L);

        assertEquals(6, batch.remaining); // không đổi
        assertEquals(2, batch.damaged); // ghi nhận damaged
        assertEquals(2, allocation.returned);
    }
}
