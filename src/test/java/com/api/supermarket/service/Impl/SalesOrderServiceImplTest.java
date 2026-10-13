package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.api.supermarket.dto.request.OrderItemRequest;
import com.api.supermarket.dto.request.PaymentConfirmationRequest;
import com.api.supermarket.dto.request.ReturnOrderRequest;
import com.api.supermarket.dto.request.SalesOrderRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.PaymentRecordResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;
import com.api.supermarket.entity.Customer;
import com.api.supermarket.entity.PaymentRecord;
import com.api.supermarket.entity.Product;
import com.api.supermarket.entity.SalesOrder;
import com.api.supermarket.entity.SalesOrderItem;
import com.api.supermarket.entity.StockMovement;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.dto.request.ReturnItemRequest;
import com.api.supermarket.dto.response.SalesOrderReturnResponse;
import com.api.supermarket.entity.SalesOrderReturn;
import com.api.supermarket.entity.SalesOrderReturnItem;
import com.api.supermarket.exception.InsufficientStockException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.CustomerRepository;
import com.api.supermarket.repository.PaymentRecordRepository;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.SalesOrderItemRepository;
import com.api.supermarket.repository.SalesOrderRepository;
import com.api.supermarket.repository.SalesOrderReturnItemRepository;
import com.api.supermarket.repository.SalesOrderReturnRepository;
import com.api.supermarket.repository.StockMovementRepository;
import com.api.supermarket.repository.UserRepository;
import com.api.supermarket.service.InventoryBatchService;

@ExtendWith(MockitoExtension.class)
public class SalesOrderServiceImplTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOrderItemRepository salesOrderItemRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private SalesOrderReturnRepository salesOrderReturnRepository;

    @Mock
    private SalesOrderReturnItemRepository salesOrderReturnItemRepository;

    @Mock
    private InventoryBatchService inventoryBatchService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SalesOrderServiceImpl salesOrderService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createSampleUser() {
        User user = new User();
        user.setUserId(1L);
        user.setUserName("admin");
        user.setFullName("Quản Trị Viên");
        return user;
    }

    private Customer createSampleCustomer() {
        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setFullName("Nguyễn Văn A");
        customer.setPhone("0987654321");
        return customer;
    }

    private Product createSampleProduct(Long id, String name, int stock, BigDecimal price) {
        Product product = new Product();
        product.setProductId(id);
        product.setProductName(name);
        product.setStockQuantity(stock);
        product.setReservedQuantity(0);
        product.setDamagedQuantity(0);
        product.setIsActive(true);
        product.setPrice(price);
        return product;
    }

    @Test
    void createSalesOrder_InStoreCash_Success_DeductsStockAndSavesPayment() {
        User user = createSampleUser();
        Customer customer = createSampleCustomer();
        Product product = createSampleProduct(1L, "Coca Cola", 10, new BigDecimal("10000.00"));

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userRepository.findByUserName("admin")).thenReturn(Optional.of(user));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        SalesOrder savedOrder = new SalesOrder();
        savedOrder.setOrderId(100L);
        savedOrder.setOrderCode("HD20260826-001");
        savedOrder.setCustomer(customer);
        savedOrder.setUser(user);
        savedOrder.setOrderType("IN_STORE");
        savedOrder.setPaymentMethod("CASH");
        savedOrder.setStatus("PAID");
        savedOrder.setPaymentStatus("PAID");
        savedOrder.setDeliveryStatus("DELIVERED");
        savedOrder.setSubtotal(new BigDecimal("20000.00"));
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("20000.00"));

        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(1L);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("10000.00"));
        item.setSubtotal(new BigDecimal("20000.00"));

        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(savedOrder);
        when(salesOrderItemRepository.saveAll(anyList())).thenReturn(List.of(item));

        SalesOrderRequest request = new SalesOrderRequest();
        request.setCustomerId(1L);
        request.setOrderType("IN_STORE");
        request.setPaymentMethod("CASH");

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(1L);
        itemReq.setQuantity(2);
        request.setItems(List.of(itemReq));

        SalesOrderResponse response = salesOrderService.createSalesOrder(request);

        assertNotNull(response);
        assertEquals("HD20260826-001", response.getOrderCode());
        assertEquals("Nguyễn Văn A", response.getCustomerName());
        assertEquals(8, product.getStockQuantity());

        verify(productRepository).save(product);
        verify(stockMovementRepository).save(any(StockMovement.class));
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
    }

    @Test
    void createSalesOrder_OnlineBankTransfer_Success_ReservesStock() {
        User user = createSampleUser();
        Customer customer = createSampleCustomer();
        customer.setUser(user);
        Product product = createSampleProduct(1L, "Sữa Tươi", 10, new BigDecimal("30000.00"));

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin");
        when(userRepository.findByUserName("admin")).thenReturn(Optional.of(user));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        SalesOrder savedOrder = new SalesOrder();
        savedOrder.setOrderId(101L);
        savedOrder.setOrderCode("HD-ONLINE-001");
        savedOrder.setCustomer(customer);
        savedOrder.setUser(user);
        savedOrder.setOrderType("ONLINE");
        savedOrder.setPaymentMethod("BANK_TRANSFER");
        savedOrder.setStatus("PENDING_PAYMENT");
        savedOrder.setPaymentStatus("PENDING");
        savedOrder.setDeliveryStatus("PENDING");
        savedOrder.setSubtotal(new BigDecimal("60000.00"));
        savedOrder.setDiscountAmount(BigDecimal.ZERO);
        savedOrder.setTotalAmount(new BigDecimal("60000.00"));

        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(2L);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("30000.00"));
        item.setSubtotal(new BigDecimal("60000.00"));

        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(savedOrder);
        when(salesOrderItemRepository.saveAll(anyList())).thenReturn(List.of(item));

        SalesOrderRequest request = new SalesOrderRequest();
        request.setCustomerId(1L);
        request.setOrderType("ONLINE");
        request.setPaymentMethod("BANK_TRANSFER");
        request.setShippingAddress("123 Quận 1");
        request.setReceiverName("Nguyễn Văn A");
        request.setReceiverPhone("0987654321");

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(1L);
        itemReq.setQuantity(2);
        request.setItems(List.of(itemReq));

        SalesOrderResponse response = salesOrderService.createSalesOrder(request);

        assertNotNull(response);
        assertEquals(10, product.getStockQuantity());
        assertEquals(2, product.getReservedQuantity());
        verify(productRepository).save(product);
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void createSalesOrder_InsufficientStock_ThrowsInsufficientStockException() {
        User user = createSampleUser();
        Product product = createSampleProduct(1L, "Bánh AFC", 2, new BigDecimal("25000.00"));

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("admin");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(userRepository.findByUserName("admin")).thenReturn(Optional.of(user));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        SalesOrderRequest request = new SalesOrderRequest();
        request.setOrderType("IN_STORE");
        request.setPaymentMethod("CASH");

        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setProductId(1L);
        itemReq.setQuantity(5);
        request.setItems(List.of(itemReq));

        InsufficientStockException ex = assertThrows(InsufficientStockException.class, () -> {
            salesOrderService.createSalesOrder(request);
        });

        assertEquals("Sản phẩm 'Bánh AFC' không đủ tồn khả dụng (còn 2, cần 5)", ex.getMessage());
    }

    @Test
    void paySalesOrder_Success() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setOrderType("ONLINE");
        order.setPaymentMethod("BANK_TRANSFER");
        order.setStatus("PENDING_PAYMENT");
        order.setPaymentStatus("PENDING");
        order.setTotalAmount(new BigDecimal("100000.00"));
        order.setPaymentExpiresAt(LocalDateTime.now().plusHours(1));

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(new ArrayList<>());

        PaymentConfirmationRequest payReq = new PaymentConfirmationRequest();
        payReq.setAmount(new BigDecimal("100000.00"));
        payReq.setReference("BANK-TXN-001");

        SalesOrderResponse response = salesOrderService.paySalesOrder(1L, payReq);

        assertNotNull(response);
        assertEquals("PAID", order.getStatus());
        assertEquals("PAID", order.getPaymentStatus());
        assertEquals("READY", order.getDeliveryStatus());
        assertNotNull(order.getPaidAt());
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
        verify(salesOrderRepository).save(order);
    }

    @Test
    void paySalesOrder_AmountMismatch_ThrowsBadRequestException() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setOrderType("ONLINE");
        order.setPaymentMethod("BANK_TRANSFER");
        order.setStatus("PENDING_PAYMENT");
        order.setTotalAmount(new BigDecimal("100000.00"));

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        PaymentConfirmationRequest payReq = new PaymentConfirmationRequest();
        payReq.setAmount(new BigDecimal("50000.00"));
        payReq.setReference("BANK-TXN-001");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> {
            salesOrderService.paySalesOrder(1L, payReq);
        });

        assertTrue(ex.getMessage().contains("số tiền bằng tổng tiền của đơn hàng"));
    }

    @Test
    void paySalesOrder_ExpiredPayment_CancelsAndMarksRefundPending() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setOrderType("ONLINE");
        order.setPaymentMethod("BANK_TRANSFER");
        order.setStatus("PENDING_PAYMENT");
        order.setTotalAmount(new BigDecimal("100000.00"));
        order.setPaymentExpiresAt(LocalDateTime.now().minusMinutes(5));

        Product product = createSampleProduct(1L, "Sữa", 10, new BigDecimal("50000.00"));
        product.setReservedQuantity(2);
        SalesOrderItem item = new SalesOrderItem();
        item.setProduct(product);
        item.setQuantity(2);

        List<SalesOrderItem> itemList = new ArrayList<>();
        itemList.add(item);

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(itemList);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);

        PaymentConfirmationRequest payReq = new PaymentConfirmationRequest();
        payReq.setAmount(new BigDecimal("100000.00"));
        payReq.setReference("BANK-TXN-001");

        SalesOrderResponse response = salesOrderService.paySalesOrder(1L, payReq);

        assertNotNull(response);
        assertEquals("CANCELLED", order.getStatus());
        assertEquals("REFUND_PENDING", order.getPaymentStatus());
        assertEquals(0, product.getReservedQuantity());
        verify(productRepository).save(product);
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
    }

    @Test
    void failPayment_Success() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setStatus("PENDING_PAYMENT");
        order.setOrderType("ONLINE");

        Product product = createSampleProduct(1L, "Trà", 10, new BigDecimal("20000.00"));
        product.setReservedQuantity(3);
        SalesOrderItem item = new SalesOrderItem();
        item.setProduct(product);
        item.setQuantity(3);

        List<SalesOrderItem> itemList = new ArrayList<>();
        itemList.add(item);

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(itemList);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);

        SalesOrderResponse response = salesOrderService.failPayment(1L);

        assertNotNull(response);
        assertEquals("CANCELLED", order.getStatus());
        assertEquals("FAILED", order.getPaymentStatus());
        assertEquals(0, product.getReservedQuantity());
    }

    @Test
    void cancelSalesOrder_OnlinePaid_SetsRefundPending() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setStatus("PAID");
        order.setPaymentStatus("PAID");
        order.setOrderType("ONLINE");

        Product product = createSampleProduct(1L, "Nước ngọt", 20, new BigDecimal("10000.00"));
        product.setReservedQuantity(5);
        SalesOrderItem item = new SalesOrderItem();
        item.setProduct(product);
        item.setQuantity(5);

        List<SalesOrderItem> itemList = new ArrayList<>();
        itemList.add(item);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(itemList);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);

        SalesOrderResponse response = salesOrderService.cancelSalesOrder(1L);

        assertNotNull(response);
        assertEquals("CANCELLED", order.getStatus());
        assertEquals("REFUND_PENDING", order.getPaymentStatus());
        assertEquals(0, product.getReservedQuantity());
    }

    @Test
    void shipSalesOrder_Success_DeductsReservedStock() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setOrderType("ONLINE");
        order.setStatus("PAID");
        order.setPaymentStatus("PAID");

        Product product = createSampleProduct(1L, "Bánh", 50, new BigDecimal("15000.00"));
        product.setReservedQuantity(10);
        SalesOrderItem item = new SalesOrderItem();
        item.setProduct(product);
        item.setQuantity(10);

        List<SalesOrderItem> itemList = new ArrayList<>();
        itemList.add(item);

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(itemList);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);

        SalesOrderResponse response = salesOrderService.shipSalesOrder(1L);

        assertNotNull(response);
        assertEquals("SHIPPING", order.getStatus());
        assertEquals("SHIPPED", order.getDeliveryStatus());
        assertEquals(40, product.getStockQuantity());
        assertEquals(0, product.getReservedQuantity());
    }

    @Test
    void deliverSalesOrder_Success_CodSetsPaid() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setOrderType("ONLINE");
        order.setStatus("SHIPPING");
        order.setPaymentMethod("COD");
        order.setOrderCode("COD123");

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(new ArrayList<>());

        SalesOrderResponse response = salesOrderService.deliverSalesOrder(1L);

        assertNotNull(response);
        assertEquals("DELIVERED", order.getStatus());
        assertEquals("DELIVERED", order.getDeliveryStatus());
        assertEquals("PAID", order.getPaymentStatus());
        assertNotNull(order.getPaidAt());
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
    }

    @Test
    void returnSalesOrder_Delivered_RestoresStock() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setStatus("DELIVERED");
        order.setPaymentStatus("PAID");
        order.setInventoryDeducted(true);

        Product product = createSampleProduct(1L, "Dầu ăn", 20, new BigDecimal("45000.00"));
        SalesOrderItem item = new SalesOrderItem();
        item.setOrderItemId(10L);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("45000.00"));

        List<SalesOrderItem> itemList = new ArrayList<>();
        itemList.add(item);

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderReturnRepository.findByRequestKey("RET-REQ-001")).thenReturn(Optional.empty());
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(itemList);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("staff1");
        when(salesOrderReturnRepository.save(any(SalesOrderReturn.class))).thenAnswer(i -> {
            SalesOrderReturn r = i.getArgument(0);
            r.setReturnId(100L);
            return r;
        });
        when(salesOrderReturnItemRepository.findBySalesOrderReturn_ReturnIdOrderByReturnItemIdAsc(100L))
                .thenReturn(Collections.emptyList());
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);

        ReturnOrderRequest req = new ReturnOrderRequest();
        req.setRequestKey("RET-REQ-001");
        ReturnItemRequest itemReq = new ReturnItemRequest();
        itemReq.setOrderItemId(10L);
        itemReq.setQuantity(2);
        itemReq.setRestock(true);
        itemReq.setReason("Hàng lỗi bao bì");
        req.setItems(List.of(itemReq));

        SalesOrderReturnResponse response = salesOrderService.returnSalesOrder(1L, req);

        assertNotNull(response);
        assertEquals(100L, response.returnId());
        assertEquals("RETURNED", order.getStatus());
        assertEquals("REFUND_PENDING", order.getPaymentStatus());
        assertEquals(22, product.getStockQuantity());
        verify(inventoryBatchService).receiveReturn(item, 2, true, null);
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void confirmReturnRefund_Success() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setTotalAmount(new BigDecimal("90000.00"));

        SalesOrderReturn salesReturn = new SalesOrderReturn();
        salesReturn.setReturnId(100L);
        salesReturn.setOrderId(1L);
        salesReturn.setRefundAmount(new BigDecimal("90000.00"));
        salesReturn.setRefundStatus("REFUND_PENDING");

        when(salesOrderReturnRepository.findById(100L)).thenReturn(Optional.of(salesReturn));
        when(salesOrderReturnRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(salesReturn));
        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderReturnRepository.sumRefundedByOrderId(1L)).thenReturn(new BigDecimal("90000.00"));
        when(salesOrderReturnRepository.existsByOrderIdAndRefundStatusIn(eq(1L), anyList())).thenReturn(false);
        when(salesOrderReturnItemRepository.findBySalesOrderReturn_ReturnIdOrderByReturnItemIdAsc(100L))
                .thenReturn(Collections.emptyList());

        PaymentConfirmationRequest req = new PaymentConfirmationRequest();
        req.setReference("REF-PAY-001");
        req.setAmount(new BigDecimal("90000.00"));

        SalesOrderReturnResponse response = salesOrderService.confirmReturnRefund(100L, req);

        assertNotNull(response);
        assertEquals("REFUNDED", salesReturn.getRefundStatus());
        assertEquals("REFUNDED", order.getPaymentStatus());
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
    }

    @Test
    void getReturns_Success() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);

        SalesOrderReturn salesReturn = new SalesOrderReturn();
        salesReturn.setReturnId(100L);
        salesReturn.setOrderId(1L);

        when(salesOrderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(salesOrderReturnRepository.findByOrderIdOrderByReturnIdAsc(1L)).thenReturn(List.of(salesReturn));
        when(salesOrderReturnItemRepository.findBySalesOrderReturn_ReturnIdOrderByReturnItemIdAsc(100L))
                .thenReturn(Collections.emptyList());

        List<SalesOrderReturnResponse> list = salesOrderService.getReturns(1L);

        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    void failDelivery_Success() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setStatus("SHIPPING");
        order.setDeliveryStatus("SHIPPING");

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);

        SalesOrderResponse response = salesOrderService.failDelivery(1L);

        assertNotNull(response);
        assertEquals("RETURN_IN_TRANSIT", order.getStatus());
        assertEquals("RETURN_IN_TRANSIT", order.getDeliveryStatus());
    }

    @Test
    void confirmRefund_Success() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setStatus("RETURNED");
        order.setPaymentStatus("REFUND_PENDING");
        order.setTotalAmount(new BigDecimal("90000.00"));

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(new ArrayList<>());

        PaymentConfirmationRequest req = new PaymentConfirmationRequest();
        req.setAmount(new BigDecimal("90000.00"));
        req.setReference("REFUND-TXN-123");

        SalesOrderResponse response = salesOrderService.confirmRefund(1L, req);

        assertNotNull(response);
        assertEquals("REFUNDED", order.getPaymentStatus());
        verify(paymentRecordRepository).save(any(PaymentRecord.class));
    }

    @Test
    void expireUnpaidOrder_Success() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setStatus("PENDING_PAYMENT");
        order.setOrderType("ONLINE");
        order.setPaymentExpiresAt(LocalDateTime.now().minusMinutes(10));

        Product product = createSampleProduct(1L, "Kẹo", 30, new BigDecimal("5000.00"));
        product.setReservedQuantity(5);
        SalesOrderItem item = new SalesOrderItem();
        item.setProduct(product);
        item.setQuantity(5);

        List<SalesOrderItem> itemList = new ArrayList<>();
        itemList.add(item);

        when(salesOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(itemList);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenReturn(order);

        salesOrderService.expireUnpaidOrder(1L);

        assertEquals("CANCELLED", order.getStatus());
        assertEquals(0, product.getReservedQuantity());
    }

    @Test
    void getSalesOrderById_NotFound_ThrowsResourceNotFoundException() {
        when(salesOrderRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> {
            salesOrderService.getSalesOrderById(999L);
        });

        assertEquals("Không tìm thấy hóa đơn với ID: 999", ex.getMessage());
    }

    @Test
    void getPaymentRecords_Success() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(100L);

        when(salesOrderRepository.findById(100L)).thenReturn(Optional.of(order));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        PaymentRecord record = new PaymentRecord();
        record.setOrderId(100L);
        record.setEventType("PAYMENT");
        record.setAmount(new BigDecimal("50000.00"));
        record.setReference("REF123");

        when(paymentRecordRepository.findByOrderIdOrderByPaymentRecordIdAsc(100L))
                .thenReturn(List.of(record));

        List<PaymentRecordResponse> result = salesOrderService.getPaymentRecords(100L);

        assertEquals(1, result.size());
        assertEquals("PAYMENT", result.get(0).eventType());
    }

    @Test
    void filterSalesOrders_Success() {
        SalesOrder order = new SalesOrder();
        order.setOrderId(1L);
        order.setOrderCode("HD-001");
        order.setStatus("PAID");
        order.setTotalAmount(new BigDecimal("100000.00"));

        Page<SalesOrder> page = new PageImpl<>(List.of(order));
        when(salesOrderRepository.filterSalesOrder(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(salesOrderItemRepository.findBySalesOrder_OrderId(1L)).thenReturn(new ArrayList<>());

        PageResponse<SalesOrderResponse> result = salesOrderService.filterSalesOrders(
                0, 10, "createdAt", "desc", "PAID", "CASH", "HD");

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("HD-001", result.getContent().get(0).getOrderCode());
    }
}
