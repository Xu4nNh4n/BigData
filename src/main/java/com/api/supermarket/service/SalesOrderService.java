package com.api.supermarket.service;

import java.util.List;

import com.api.supermarket.dto.request.SalesOrderRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;
import com.api.supermarket.entity.Cart;

public interface SalesOrderService {

    // Lấy 1 hóa đơn cụ thể
    SalesOrderResponse getSalesOrderById(Long orderId);

    // Lấy tất cả các hóa đơn
    PageResponse<SalesOrderResponse> getAllSalesOrders();

    // Tạo hóa đơn mới
    SalesOrderResponse createSalesOrder(SalesOrderRequest request);

    SalesOrderResponse checkoutCart(Cart cart, SalesOrderRequest request);

    // Hủy hóa đơn
    SalesOrderResponse cancelSalesOrder(Long orderId);

    // Xác nhận thanh toán
    SalesOrderResponse paySalesOrder(Long orderId, com.api.supermarket.dto.request.PaymentConfirmationRequest request);

    SalesOrderResponse shipSalesOrder(Long orderId);

    SalesOrderResponse deliverSalesOrder(Long orderId);

    com.api.supermarket.dto.response.SalesOrderReturnResponse returnSalesOrder(Long orderId,
            com.api.supermarket.dto.request.ReturnOrderRequest request);

    com.api.supermarket.dto.response.SalesOrderReturnResponse confirmReturnRefund(Long returnId,
            com.api.supermarket.dto.request.PaymentConfirmationRequest request);

    SalesOrderResponse confirmRefund(Long orderId, com.api.supermarket.dto.request.PaymentConfirmationRequest request);

    List<com.api.supermarket.dto.response.SalesOrderReturnResponse> getReturns(Long orderId);

    SalesOrderResponse failDelivery(Long orderId);

    void expireUnpaidOrder(Long orderId);

    PageResponse<SalesOrderResponse> getMyOnlineOrders(int page, int size);

    List<com.api.supermarket.dto.response.PaymentRecordResponse> getPaymentRecords(Long orderId);

    // Tìm Kiếm theo mã in trên hóa đơn
    SalesOrderResponse getSalesOrderCode(String orderCode);

    // Lọc hóa đơn theo trạng thái
    PageResponse<SalesOrderResponse> filterSalesOrders(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String status,
            String paymentMethod,
            String searchTerm);
}
