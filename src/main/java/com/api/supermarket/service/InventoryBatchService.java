package com.api.supermarket.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import com.api.supermarket.domain.FefoPolicy;
import com.api.supermarket.dto.request.ReceiveBatchRequest;
import com.api.supermarket.entity.*;
import com.api.supermarket.exception.*;
import com.api.supermarket.repository.*;
import com.api.supermarket.integration.DataSyncPublisher;

@Service
@Transactional
public class InventoryBatchService {
    private final InventoryBatchRepository batches;
    private final BatchAllocationRepository allocations;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final StockMovementRepository movements;
    private final DataSyncPublisher dataSync;

    public InventoryBatchService(InventoryBatchRepository batches, BatchAllocationRepository allocations,
            ProductRepository products, SupplierRepository suppliers, StockMovementRepository movements, DataSyncPublisher dataSync) {
        this.batches = batches;
        this.allocations = allocations;
        this.products = products;
        this.suppliers = suppliers;
        this.movements = movements;
        this.dataSync = dataSync;
    }

    public InventoryBatch receive(ReceiveBatchRequest request) {
        Product product = products.findByIdForUpdate(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        String code = request.batchCode() == null ? "" : request.batchCode().trim();
        if (code.isBlank()) code = String.valueOf(batches.nextSimpleCodeValue());
        InventoryBatch existing = batches.findByCode(code).orElse(null);
        Long supplierId = request.supplierId() == null ? product.getSupplier().getSupplierId() : request.supplierId();
        if (existing != null) {
            if (!existing.productId.equals(product.getProductId()) || existing.received != request.quantity()
                    || !Objects.equals(existing.supplierId, supplierId)
                    || existing.unitCost.compareTo(request.unitCost()) != 0
                    || !Objects.equals(existing.manufacturingDate, request.manufacturingDate())
                    || !Objects.equals(existing.expiryDate, request.expiryDate())) {
                throw new BadRequestException("Mã lô đã được nhập với nội dung khác");
            }
            return existing;
        }
        validateDates(request.manufacturingDate(), request.expiryDate());
        if (!product.getIsActive()) throw new BadRequestException("Sản phẩm đã ngừng kinh doanh");
        Supplier supplier = suppliers.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp"));
        if (!Boolean.TRUE.equals(supplier.getIsActive())) throw new BadRequestException("Nhà cung cấp đã ngừng hoạt động");
        InventoryBatch batch = newBatch(product, code, request.quantity(), request.unitCost(), request.expiryDate());
        batch.supplierId = supplierId;
        batch.manufacturingDate = request.manufacturingDate();
        product.setStockQuantity(Math.addExact(product.getStockQuantity(), request.quantity()));
        products.save(product);
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType("RECEIPT");
        movement.setQuantity(request.quantity());
        movement.setNote("Nhập lô " + code);
        movement.setPerformedBy(actor());
        dataSync.movement(movements.save(movement));
        return batches.save(batch);
    }

    @Transactional(readOnly = true)
    public int usableQuantity(Long productId) {
        return Math.toIntExact(batches.usableQuantity(productId, LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public List<com.api.supermarket.dto.response.BatchAllocationResponse> getAllocations(Long orderItemId) {
        List<BatchAllocation> lines = allocations.findByOrderItemIdOrderByIdAsc(orderItemId);
        Map<Long, InventoryBatch> byId = new HashMap<>();
        batches.findAllById(lines.stream().map(line -> line.batchId).toList())
                .forEach(batch -> byId.put(batch.id, batch));
        return lines.stream().map(line -> {
            InventoryBatch batch = byId.get(line.batchId);
            if (batch == null) throw new BadRequestException("Thiếu dữ liệu lô đã cấp phát");
            return new com.api.supermarket.dto.response.BatchAllocationResponse(batch.id, batch.code,
                    batch.expiryDate, line.quantity, line.returned, line.status);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryBatch> list(Long productId) {
        if (productId == null) return batches.findAllByOrderByIdAsc();
        if (!products.existsById(productId)) throw new ResourceNotFoundException("Không tìm thấy sản phẩm");
        return batches.findByProductIdOrderByIdAsc(productId);
    }

    // Caller holds the product lock and changes aggregate stock in the same transaction.
    public InventoryBatch adjustBatch(Long batchId, com.api.supermarket.dto.request.StockAdjustmentRequest request) {
        if (request.getQuantity() >= 0) throw new BadRequestException("Điều chỉnh lô chỉ giảm tồn; tăng tồn dùng nhập lô mới");
        InventoryBatch original = batches.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lô"));
        Product product = products.findByIdForUpdate(original.productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        InventoryBatch batch = batches.lockProductBatches(product.getProductId()).stream()
                .filter(candidate -> candidate.id.equals(batchId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lô"));
        long removed = -(long) request.getQuantity();
        if (removed > batch.remaining - batch.reserved || removed > product.getStockQuantity() - product.getReservedQuantity()) {
            throw new BadRequestException("Không được giảm quá lượng chưa giữ của lô");
        }
        batch.remaining -= (int) removed;
        product.setStockQuantity(product.getStockQuantity() - (int) removed);
        products.save(product);
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setMovementType("ADJUSTMENT");
        movement.setQuantity(request.getQuantity());
        String note = "Lô " + batch.code + ": " + request.getReason().trim();
        movement.setNote(note.substring(0, Math.min(255, note.length())));
        movement.setPerformedBy(actor());
        dataSync.movement(movements.save(movement));
        return batches.save(batch);
    }

    // Caller holds the product lock and changes aggregate stock in the same transaction.
    public void opening(Product product) {
        if (product.getStockQuantity() > 0) {
            batches.save(newBatch(product, String.valueOf(batches.nextSimpleCodeValue()), product.getStockQuantity(),
                    BigDecimal.ZERO, product.getExpiryDate()));
        }
    }

    public void allocate(SalesOrderItem item, boolean reserve) {
        List<InventoryBatch> locked = batches.lockProductBatches(item.getProduct().getProductId());
        Map<Long, InventoryBatch> byId = new HashMap<>();
        locked.forEach(batch -> byId.put(batch.id, batch));
        List<FefoPolicy.Lot> balances = locked.stream().filter(batch -> "ACTIVE".equals(batch.status))
                .map(batch -> new FefoPolicy.Lot(batch.id, batch.remaining - batch.reserved,
                        batch.expiryDate, batch.receivedAt)).toList();
        List<FefoPolicy.Pick> plan;
        try {
            plan = FefoPolicy.allocate(balances, item.getQuantity(), LocalDate.now());
        } catch (IllegalArgumentException ex) {
            throw new InsufficientStockException(ex.getMessage());
        }
        for (FefoPolicy.Pick pick : plan) {
            InventoryBatch batch = byId.get(pick.lotId());
            if (reserve) batch.reserved = Math.addExact(batch.reserved, pick.quantity());
            else batch.remaining -= pick.quantity();
            BatchAllocation allocation = new BatchAllocation();
            allocation.orderItemId = item.getOrderItemId();
            allocation.batchId = batch.id;
            allocation.quantity = pick.quantity();
            allocation.status = reserve ? "RESERVED" : "CONSUMED";
            allocations.save(allocation);
        }
        batches.saveAll(locked);
    }

    public void consumeReserved(SalesOrderItem item) {
        Map<Long, InventoryBatch> locked = lockMap(item);
        List<BatchAllocation> lines = allocations.findByOrderItemIdOrderByIdAsc(item.getOrderItemId());
        requireAllocated(item, lines);
        for (BatchAllocation line : lines) {
            InventoryBatch batch = locked.get(line.batchId);
            if (!"RESERVED".equals(line.status) || batch == null || batch.reserved < line.quantity
                    || batch.remaining < line.quantity || !"ACTIVE".equals(batch.status)
                    || (batch.expiryDate != null && batch.expiryDate.isBefore(LocalDate.now()))) {
                throw new BadRequestException("Lô đã giữ không còn hợp lệ để xuất; cần hủy đơn và đối soát");
            }
            batch.reserved -= line.quantity;
            batch.remaining -= line.quantity;
            line.status = "CONSUMED";
        }
        batches.saveAll(locked.values());
        allocations.saveAll(lines);
    }

    public void releaseReserved(SalesOrderItem item) {
        Map<Long, InventoryBatch> locked = lockMap(item);
        List<BatchAllocation> lines = allocations.findByOrderItemIdOrderByIdAsc(item.getOrderItemId());
        requireAllocated(item, lines);
        for (BatchAllocation line : lines) {
            InventoryBatch batch = locked.get(line.batchId);
            if (!"RESERVED".equals(line.status) || batch == null || batch.reserved < line.quantity) {
                throw new BadRequestException("Thông tin giữ lô không khớp đơn hàng");
            }
            batch.reserved -= line.quantity;
            line.status = "RELEASED";
        }
        batches.saveAll(locked.values());
        allocations.saveAll(lines);
    }

    public void receiveReturn(SalesOrderItem item, int quantity, boolean restock, Long batchId) {
        Map<Long, InventoryBatch> locked = lockMap(item);
        List<BatchAllocation> lines = allocations.findByOrderItemIdOrderByIdAsc(item.getOrderItemId());
        if (lines.isEmpty()) {
            // Historical sales have no provable lot; quarantine rather than inventing provenance.
            if (restock || batchId != null) throw new BadRequestException("Đơn cũ thiếu nguồn gốc lô; nhận vào hàng cách ly với restock=false, không tự gán lô");
            InventoryBatch quarantine = newBatch(item.getProduct(), "LEGACY-RETURN-" + UUID.randomUUID(),
                    0, BigDecimal.ZERO, null);
            quarantine.damaged = quantity;
            quarantine.status = "QUARANTINED";
            batches.save(quarantine);
            return;
        }
        List<BatchAllocation> outstanding = lines.stream().filter(line -> line.returned < line.quantity).toList();
        long outstandingQuantity = outstanding.stream().mapToLong(line -> line.quantity - line.returned).sum();
        if (batchId == null && outstanding.size() > 1 && quantity != outstandingQuantity) {
            throw new BadRequestException("Trả một phần món xuất từ nhiều lô phải xác định batchId trên hàng thực tế");
        }
        if (batchId != null && outstanding.stream().noneMatch(line -> line.batchId.equals(batchId))) {
            throw new BadRequestException("Lô trả không thuộc phần hàng đã xuất còn được trả của món này");
        }
        int remaining = quantity;
        for (BatchAllocation line : lines) {
            if (batchId != null && !line.batchId.equals(batchId)) continue;
            int receive = Math.min(remaining, line.quantity - line.returned);
            if (receive <= 0) continue;
            InventoryBatch batch = locked.get(line.batchId);
            if (!"CONSUMED".equals(line.status) || batch == null) {
                throw new BadRequestException("Mặt hàng chưa được xuất từ lô");
            }
            if (restock) {
                if (!"ACTIVE".equals(batch.status)
                        || (batch.expiryDate != null && batch.expiryDate.isBefore(LocalDate.now()))) {
                    throw new BadRequestException("Lô trả đã hết hạn/bị cách ly; phải nhận với restock=false");
                }
                batch.remaining = Math.addExact(batch.remaining, receive);
            } else batch.damaged = Math.addExact(batch.damaged, receive);
            line.returned += receive;
            remaining -= receive;
            if (remaining == 0) break;
        }
        if (remaining != 0) throw new BadRequestException("Số lượng trả vượt lượng đã xuất từ lô");
        batches.saveAll(locked.values());
        allocations.saveAll(lines);
    }

    public void restoreCancelledStock(SalesOrderItem item) {
        if (!allocations.findByOrderItemIdOrderByIdAsc(item.getOrderItemId()).isEmpty()) {
            receiveReturn(item, item.getQuantity(), true, null);
            return;
        }
        // Legacy pending orders deducted aggregate stock before any shipment.
        // Restore the explicit migration balance, not an invented original supplier lot.
        List<InventoryBatch> locked = batches.lockProductBatches(item.getProduct().getProductId());
        InventoryBatch baseline = locked.stream()
                .filter(batch -> batch.code.equals("BASELINE-" + item.getProduct().getProductId()))
                .findFirst().orElseThrow(() -> new BadRequestException("Thiếu số dư lô nâng cấp để hủy đơn cũ"));
        baseline.remaining = Math.addExact(baseline.remaining, item.getQuantity());
        batches.save(baseline);
    }

    private Map<Long, InventoryBatch> lockMap(SalesOrderItem item) {
        Map<Long, InventoryBatch> result = new HashMap<>();
        batches.lockProductBatches(item.getProduct().getProductId()).forEach(batch -> result.put(batch.id, batch));
        return result;
    }

    private void requireAllocated(SalesOrderItem item, List<BatchAllocation> lines) {
        long quantity = lines.stream().mapToLong(line -> line.quantity).sum();
        if (quantity != item.getQuantity()) throw new BadRequestException("Thiếu cấp phát lô cho đơn hàng");
    }

    private InventoryBatch newBatch(Product product, String code, int quantity, BigDecimal cost, LocalDate expiry) {
        InventoryBatch batch = new InventoryBatch();
        batch.productId = product.getProductId();
        batch.supplierId = product.getSupplier().getSupplierId();
        batch.code = code;
        batch.received = quantity;
        batch.remaining = quantity;
        batch.unitCost = cost;
        batch.expiryDate = expiry;
        batch.receivedAt = LocalDateTime.now();
        batch.receivedBy = actor();
        return batch;
    }

    private void validateDates(LocalDate manufactured, LocalDate expiry) {
        if (manufactured != null && manufactured.isAfter(LocalDate.now()))
            throw new BadRequestException("Ngày sản xuất không được ở tương lai");
        if (expiry != null && (expiry.isBefore(LocalDate.now())
                || (manufactured != null && expiry.isBefore(manufactured))))
            throw new BadRequestException("Hạn sử dụng không hợp lệ");
    }

    private String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "SYSTEM" : authentication.getName();
    }
}
