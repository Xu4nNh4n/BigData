package com.api.supermarket.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.request.PaymentConfirmationRequest;
import com.api.supermarket.dto.request.ReturnOrderRequest;
import com.api.supermarket.dto.request.SalesOrderRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.PaymentRecordResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;
import com.api.supermarket.service.SalesOrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Sales Orders - Quản lý đơn hàng & Hóa đơn", description = "Order processing lifecycle (POS checkout, online reservation, payment, shipping, delivery, return, refund) - Quy trình vòng đời đơn hàng siêu thị")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;
    private static final Logger log = LoggerFactory.getLogger(SalesOrderController.class);

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get my online orders - Xem đơn hàng online của tôi", description = "Retrieve list of online orders belonging to the currently logged-in customer - Xem danh sách đơn hàng online của tài khoản khách hàng hiện tại")
    public ResponseEntity<PageResponse<SalesOrderResponse>> getMyOnlineOrders(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(salesOrderService.getMyOnlineOrders(page, size));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Create sales order - Tạo hóa đơn bán hàng mới", description = "Create an order directly at POS counter or online - Tạo đơn hàng trực tiếp tại quầy thu ngân hoặc online")
    public ResponseEntity<SalesOrderResponse> createSalesOrder(@Valid @RequestBody SalesOrderRequest request) {
        log.info("Nhận yêu cầu tạo hóa đơn mới từ thu ngân");
        SalesOrderResponse response = salesOrderService.createSalesOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get order by ID - Xem chi tiết hóa đơn theo ID", description = "Retrieve complete sales order information by order ID - Lấy thông tin chi tiết của một hóa đơn theo mã ID")
    public ResponseEntity<SalesOrderResponse> getSalesOrderById(@PathVariable Long id) {
        log.debug("Xem chi tiết hóa đơn, orderId = {}", id);
        return ResponseEntity.ok(salesOrderService.getSalesOrderById(id));
    }

    @GetMapping("/code/{orderCode}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get order by code - Tra cứu hóa đơn theo mã in", description = "Find sales order by printed receipt code - Tra cứu hóa đơn bán hàng bằng mã in trên phiếu thu")
    public ResponseEntity<SalesOrderResponse> getSalesOrderByCode(@PathVariable String orderCode) {
        log.debug("Tra cứu hóa đơn theo mã: {}", orderCode);
        return ResponseEntity.ok(salesOrderService.getSalesOrderCode(orderCode));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Filter sales orders - Lọc và phân trang danh sách hóa đơn", description = "Paginate and filter orders by status, payment method, or search term - Phân trang và tìm kiếm đơn hàng theo trạng thái, hình thức thanh toán")
    public ResponseEntity<PageResponse<SalesOrderResponse>> filterSalesOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String searchTerm) {
        PageResponse<SalesOrderResponse> response = salesOrderService.filterSalesOrders(page, size, sortBy, sortDir,
                status, paymentMethod, searchTerm);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Confirm payment - Xác nhận thanh toán hóa đơn", description = "Confirm bank transfer payment for an online order - Xác nhận thanh toán chuyển khoản cho đơn hàng trực tuyến")
    public ResponseEntity<SalesOrderResponse> paySalesOrder(@PathVariable Long id,
            @Valid @RequestBody PaymentConfirmationRequest request) {
        log.info("Xác nhận thanh toán cho hóa đơn ID: {}", id);
        return ResponseEntity.ok(salesOrderService.paySalesOrder(id, request));
    }

    @PutMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Ship order - Bắt đầu giao hàng", description = "Start delivery process for paid or COD online order and deduct reserved inventory - Bắt đầu giao hàng cho đơn online và trừ tồn kho giữ")
    public ResponseEntity<SalesOrderResponse> shipSalesOrder(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.shipSalesOrder(id));
    }

    @PutMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Confirm delivery - Xác nhận giao hàng thành công", description = "Confirm successful customer delivery (collects COD payment if applicable) - Xác nhận đơn đã giao thành công và thu tiền nếu là đơn COD")
    public ResponseEntity<SalesOrderResponse> deliverSalesOrder(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.deliverSalesOrder(id));
    }

    @PutMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Return items and create return receipt - Trả từng món và ghi nhận phiếu trả", description = "Return items with restock/quarantine status and restore to source batches - Nhận hàng trả thực tế, phân loại bán được/cách ly, giới hạn theo lượng đã mua và hoàn về lô gốc; hoàn tiền xác nhận riêng theo returnId")
    public ResponseEntity<com.api.supermarket.dto.response.SalesOrderReturnResponse> returnSalesOrder(@PathVariable Long id,
            @Valid @RequestBody ReturnOrderRequest request) {
        return ResponseEntity.ok(salesOrderService.returnSalesOrder(id, request));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel sales order - Hủy hóa đơn bán hàng", description = "Cancel pending or paid order and return items to stock - Hủy đơn hàng và hoàn lại tồn kho đã giữ hoặc đã xuất")
    public ResponseEntity<SalesOrderResponse> cancelSalesOrder(@PathVariable Long id) {
        log.info("Yêu cầu hủy hóa đơn ID: {}", id);
        return ResponseEntity.ok(salesOrderService.cancelSalesOrder(id));
    }

    @PutMapping("/{id}/delivery-failed")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Mark delivery failed - Đánh dấu giao hàng thất bại", description = "Mark order delivery as failed, transition to RETURN_IN_TRANSIT - Chuyển đơn sang RETURN_IN_TRANSIT; chỉ cộng kho khi nhân viên thực sự nhận hàng bằng phiếu trả")
    public ResponseEntity<SalesOrderResponse> failDelivery(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.failDelivery(id));
    }

    @PutMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Confirm refund - Xác nhận hoàn tiền cho khách", description = "Confirm money refund for cancelled or returned order - Xác nhận hoàn tiền cho khách hàng đối với đơn đã hủy hoặc trả hàng")
    public ResponseEntity<SalesOrderResponse> confirmRefund(@PathVariable Long id,
            @Valid @RequestBody PaymentConfirmationRequest request) {
        return ResponseEntity.ok(salesOrderService.confirmRefund(id, request));
    }

    @PutMapping("/returns/{returnId}/refund")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Confirm return refund - Xác nhận hoàn tiền cho phiếu trả hàng", description = "Confirm refund transaction for a specific return receipt - Xác nhận hoàn tiền theo phiếu trả hàng cụ thể")
    public ResponseEntity<com.api.supermarket.dto.response.SalesOrderReturnResponse> confirmReturnRefund(
            @PathVariable Long returnId, @Valid @RequestBody PaymentConfirmationRequest request) {
        return ResponseEntity.ok(salesOrderService.confirmReturnRefund(returnId, request));
    }

    @GetMapping("/{id}/returns")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get order return receipts - Danh sách phiếu trả hàng của hóa đơn", description = "Retrieve list of all return receipts for an order - Lấy danh sách tất cả các phiếu trả hàng của một đơn hàng")
    public ResponseEntity<List<com.api.supermarket.dto.response.SalesOrderReturnResponse>> getReturns(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.getReturns(id));
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Get payment records - Xem lịch sử thanh toán hóa đơn", description = "Retrieve list of all payment and refund transactions of an order - Lấy lịch sử chi tiết các giao dịch thanh toán và hoàn tiền của đơn")
    public ResponseEntity<List<PaymentRecordResponse>> getPaymentRecords(@PathVariable Long id) {
        return ResponseEntity.ok(salesOrderService.getPaymentRecords(id));
    }
}
