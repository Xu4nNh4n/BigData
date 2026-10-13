package com.api.supermarket.service.Impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import com.api.supermarket.dto.request.OrderItemRequest;
import com.api.supermarket.dto.request.PaymentConfirmationRequest;
import com.api.supermarket.dto.request.ReturnItemRequest;
import com.api.supermarket.dto.request.ReturnOrderRequest;
import com.api.supermarket.dto.request.SalesOrderRequest;
import com.api.supermarket.dto.response.OrderItemResponse;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.ProductResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;
import com.api.supermarket.entity.Cart;
import com.api.supermarket.entity.Customer;
import com.api.supermarket.entity.Product;
import com.api.supermarket.entity.PaymentRecord;
import com.api.supermarket.entity.SalesOrder;
import com.api.supermarket.entity.SalesOrderItem;
import com.api.supermarket.entity.SalesOrderReturn;
import com.api.supermarket.entity.SalesOrderReturnItem;
import com.api.supermarket.entity.StockMovement;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.InsufficientStockException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.CustomerRepository;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.PaymentRecordRepository;
import com.api.supermarket.repository.SalesOrderItemRepository;
import com.api.supermarket.repository.SalesOrderReturnItemRepository;
import com.api.supermarket.repository.SalesOrderReturnRepository;
import com.api.supermarket.repository.SalesOrderRepository;
import com.api.supermarket.repository.StockMovementRepository;
import com.api.supermarket.repository.UserRepository;
import com.api.supermarket.service.SalesOrderService;
import com.api.supermarket.validation.PaginationValidator;
import com.api.supermarket.integration.DataSyncPublisher;

@Service
public class SalesOrderServiceImpl implements SalesOrderService {
    private static final String IN_STORE = "IN_STORE";
    private static final String ONLINE = "ONLINE";
    private static final String CASH = "CASH";
    private static final String BANK_TRANSFER = "BANK_TRANSFER";
    private static final String COD = "COD";

    private final SalesOrderItemRepository salesOrderItemRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final SalesOrderReturnRepository salesOrderReturnRepository;
    private final SalesOrderReturnItemRepository salesOrderReturnItemRepository;
    private final com.api.supermarket.service.InventoryBatchService inventoryBatchService;
    private final DataSyncPublisher dataSync;

    @Value("${business.payment-timeout-minutes:30}")
    private long paymentTimeoutMinutes;

    public SalesOrderServiceImpl(SalesOrderRepository salesOrderRepository, CustomerRepository customerRepository,
            ProductRepository productRepository, SalesOrderItemRepository salesOrderItemRepository,
            UserRepository userRepository, StockMovementRepository stockMovementRepository,
            PaymentRecordRepository paymentRecordRepository, SalesOrderReturnRepository salesOrderReturnRepository,
            SalesOrderReturnItemRepository salesOrderReturnItemRepository,
            com.api.supermarket.service.InventoryBatchService inventoryBatchService, DataSyncPublisher dataSync) {
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.userRepository = userRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.salesOrderReturnRepository = salesOrderReturnRepository;
        this.salesOrderReturnItemRepository = salesOrderReturnItemRepository;
        this.inventoryBatchService = inventoryBatchService;
        this.dataSync = dataSync;
    }

    @Override
    @Transactional
    public SalesOrderResponse createSalesOrder(SalesOrderRequest request) {
        return createOrder(request);
    }

    @Override
    @Transactional
    public SalesOrderResponse checkoutCart(Cart cart, SalesOrderRequest request) {
        if (request == null || cart == null || !"OPEN".equals(cart.getStatus())
                || !cart.getCartType().equals(request.getOrderType())) {
            throw new BadRequestException("Giỏ hàng không hợp lệ để thanh toán");
        }
        return createOrder(request);
    }

    private SalesOrderResponse createOrder(SalesOrderRequest request) {
        if (request == null) {
            throw new BadRequestException("Dữ liệu đơn hàng không được để trống");
        }
        String orderType = normalize(request.getOrderType());
        String paymentMethod = normalize(request.getPaymentMethod());
        validateOrderInput(orderType, paymentMethod, request);

        User user = currentUser();
        if (ONLINE.equals(orderType) && (user == null || !"CUSTOMER".equals(user.getRole().getRoleName()))) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ tài khoản CUSTOMER được đặt đơn online");
        }
        if (IN_STORE.equals(orderType) && (user == null || !isStaff())) {
            throw new BadRequestException("Đơn tại cửa hàng phải có nhân viên thao tác");
        }

        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng"));
        }
        if (ONLINE.equals(orderType) && customer == null) {
            throw new BadRequestException("Đơn ONLINE phải gắn với khách hàng");
        }
        if (customer != null && !Boolean.TRUE.equals(customer.getIsActive())) {
            throw new BadRequestException("Hồ sơ khách hàng đã bị khóa");
        }
        if (ONLINE.equals(orderType)
                && (user == null || customer.getUser() == null
                        || !customer.getUser().getUserId().equals(user.getUserId()))) {
            throw new org.springframework.security.access.AccessDeniedException("Không thể đặt hàng bằng hồ sơ khách hàng khác");
        }

        Map<Long, Integer> quantities = mergeQuantities(request.getItems());
        if (quantities.isEmpty()) {
            throw new BadRequestException("Đơn hàng phải có ít nhất một sản phẩm");
        }

        SalesOrder order = new SalesOrder();
        // The database order id is the stable internal sequence. After the first
        // save we expose that same value as the short, human-readable order code.
        // A temporary unique value is required only while JPA obtains the id.
        order.setOrderCode("TEMP-" + System.nanoTime());
        order.setOrderDate(LocalDateTime.now());
        order.setCustomer(customer);
        order.setUser(user);
        order.setOrderType(orderType);
        order.setPaymentMethod(paymentMethod);
        order.setNote(request.getNote());
        order.setShippingAddress(request.getShippingAddress());
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());

        if (IN_STORE.equals(orderType)) {
            order.setInventoryDeducted(true);
            order.setStatus("PAID");
            order.setPaymentStatus("PAID");
            order.setDeliveryStatus("NOT_REQUIRED");
            order.setPaidAt(LocalDateTime.now());
        } else if (COD.equals(paymentMethod)) {
            order.setStatus("COD_PENDING");
            order.setPaymentStatus("COD_PENDING");
            order.setDeliveryStatus("READY");
        } else {
            order.setStatus("PENDING_PAYMENT");
            order.setPaymentStatus("PENDING");
            order.setDeliveryStatus("WAITING_PAYMENT");
            order.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(Math.max(1, paymentTimeoutMinutes)));
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        List<SalesOrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + entry.getKey()));
            int quantity = entry.getValue();
            if (!product.getIsActive()) {
                throw new BadRequestException("Sản phẩm đã ngừng kinh doanh: " + product.getProductName());
            }
            if (product.getAvailableQuantity() < quantity) {
                throw new InsufficientStockException("Sản phẩm '" + product.getProductName()
                        + "' không đủ tồn khả dụng (còn " + product.getAvailableQuantity() + ", cần " + quantity + ")");
            }

            if (IN_STORE.equals(orderType)) {
                product.setStockQuantity(product.getStockQuantity() - quantity);
            } else {
                product.setReservedQuantity(Math.addExact(product.getReservedQuantity(), quantity));
            }
            productRepository.save(product);

            BigDecimal unitPrice = product.getPrice();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(itemSubtotal);

            SalesOrderItem orderItem = new SalesOrderItem();
            orderItem.setSalesOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setSubtotal(itemSubtotal);
            orderItems.add(orderItem);
        }

        order.setSubtotal(subtotal);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(subtotal);

        SalesOrder savedOrder = salesOrderRepository.save(order);
        savedOrder.setOrderCode(String.valueOf(savedOrder.getOrderId()));
        savedOrder = salesOrderRepository.save(savedOrder);
        List<SalesOrderItem> savedItems = salesOrderItemRepository.saveAll(orderItems);
        for (SalesOrderItem item : savedItems) {
            inventoryBatchService.allocate(item, !IN_STORE.equals(orderType));
            saveMovement(item.getProduct(), savedOrder.getOrderId(), IN_STORE.equals(orderType) ? "SALE" : "RESERVE",
                    -item.getQuantity(), IN_STORE.equals(orderType) ? "Xuất kho tại quầy" : "Giữ hàng cho đơn online");
        }
        if (IN_STORE.equals(orderType)) {
            recordPayment(savedOrder, "PAYMENT", CASH.equals(paymentMethod)
                    ? "POS-" + savedOrder.getOrderCode() : request.getPaymentReference().trim());
        }
        dataSync.order(savedOrder, "ORDER_CREATED", "", savedOrder.getTotalAmount(), Map.of());
        return mapToResponse(savedOrder, savedItems);
    }

    @Override
    @Transactional
    public SalesOrderResponse paySalesOrder(Long orderId, PaymentConfirmationRequest request) {
        SalesOrder order = loadOrderForUpdate(orderId);
        validatePaymentConfirmation(order, request);
        if (!ONLINE.equals(order.getOrderType()) || !BANK_TRANSFER.equals(order.getPaymentMethod())) {
            throw new BadRequestException("API này xác nhận chuyển khoản của đơn online. POS thu tiền tại checkout, COD thu tiền khi giao");
        }
        if (Set.of("PAID", "REFUND_PENDING", "REFUNDED").contains(order.getPaymentStatus())) {
            ensureMatchingPayment(order, "PAYMENT", request);
            return mapToResponse(order);
        }
        if ("CANCELLED".equals(order.getStatus())) {
            // Tiền đến sau khi hủy/hết hạn phải được ghi nhận để hoàn tiền, không tạo lại đơn hoặc giữ hàng lại.
            order.setPaymentStatus("REFUND_PENDING");
            order.setPaidAt(LocalDateTime.now());
            recordPayment(order, "PAYMENT", request.getReference().trim());
            dataSync.order(order, "PAYMENT_LATE", "", BigDecimal.ZERO, Map.of());
            return mapToResponse(salesOrderRepository.save(order));
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BadRequestException("Chỉ đơn PENDING_PAYMENT mới được thanh toán");
        }
        if (COD.equalsIgnoreCase(order.getPaymentMethod())) {
            throw new BadRequestException("Đơn COD chỉ thanh toán khi giao hàng");
        }
        if (order.getPaymentExpiresAt() != null && !order.getPaymentExpiresAt().isAfter(LocalDateTime.now())) {
            releaseInventory(order, "PAYMENT_EXPIRED");
            order.setStatus("CANCELLED");
            order.setDeliveryStatus("CANCELLED");
            order.setPaymentStatus("REFUND_PENDING");
            order.setPaidAt(LocalDateTime.now());
            recordPayment(order, "PAYMENT", request.getReference().trim());
            dataSync.order(order, "ORDER_EXPIRED", "", BigDecimal.ZERO, Map.of());
            return mapToResponse(salesOrderRepository.save(order));
        }
        order.setStatus("PAID");
        order.setPaymentStatus("PAID");
        order.setDeliveryStatus("READY");
        order.setPaidAt(LocalDateTime.now());
        recordPayment(order, "PAYMENT", request.getReference().trim());
        dataSync.order(order, "ORDER_PAYMENT_CONFIRMED", "", BigDecimal.ZERO, Map.of());
        return mapToResponse(salesOrderRepository.save(order));
    }

    @Override
    @Transactional
    public SalesOrderResponse cancelSalesOrder(Long orderId) {
        SalesOrder order = loadOrderForUpdate(orderId);
        ensureOrderAccess(order);
        if ("CANCELLED".equals(order.getStatus())) {
            return mapToResponse(order);
        }
        if (!Set.of("PENDING_PAYMENT", "PAID", "COD_PENDING").contains(order.getStatus())) {
            throw new BadRequestException("Đơn hàng không còn được phép hủy");
        }
        if (IN_STORE.equalsIgnoreCase(order.getOrderType()) && !"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BadRequestException("Đơn tại cửa hàng đã thanh toán, hãy xử lý theo nghiệp vụ trả hàng");
        }
        if ("SHIPPING".equals(order.getStatus())) {
            throw new BadRequestException("Đơn đang giao hàng, không thể hủy trực tiếp");
        }

        releaseInventory(order, "CANCEL");
        if ("PAID".equals(order.getPaymentStatus())) {
            order.setPaymentStatus("REFUND_PENDING");
        } else if (!"FAILED".equals(order.getPaymentStatus())) {
            order.setPaymentStatus("CANCELLED");
        }
        order.setStatus("CANCELLED");
        order.setDeliveryStatus("CANCELLED");
        dataSync.order(order, "ORDER_CANCELLED", "", BigDecimal.ZERO, Map.of());
        return mapToResponse(salesOrderRepository.save(order));
    }

    @Override
    @Transactional
    public SalesOrderResponse shipSalesOrder(Long orderId) {
        SalesOrder order = loadOrderForUpdate(orderId);
        if ("SHIPPING".equals(order.getStatus())) {
            return mapToResponse(order);
        }
        if (!ONLINE.equalsIgnoreCase(order.getOrderType())) {
            throw new BadRequestException("Chỉ đơn ONLINE mới có trạng thái giao hàng");
        }
        if (!"PAID".equals(order.getPaymentStatus()) && !COD.equalsIgnoreCase(order.getPaymentMethod())) {
            throw new BadRequestException("Đơn phải được thanh toán trước khi giao, trừ đơn COD");
        }
        if (!"PAID".equals(order.getStatus()) && !"COD_PENDING".equals(order.getStatus())) {
            throw new BadRequestException("Trạng thái đơn không thể chuyển sang SHIPPING");
        }
        deductReservedInventory(order);
        order.setStatus("SHIPPING");
        order.setDeliveryStatus("SHIPPED");
        dataSync.order(order, "ORDER_SHIPPED", "", BigDecimal.ZERO, Map.of());
        return mapToResponse(salesOrderRepository.save(order));
    }

    @Override
    @Transactional
    public SalesOrderResponse deliverSalesOrder(Long orderId) {
        SalesOrder order = loadOrderForUpdate(orderId);
        if ("DELIVERED".equals(order.getStatus())) {
            return mapToResponse(order);
        }
        if (!"SHIPPING".equals(order.getStatus())) {
            throw new BadRequestException("Chỉ đơn SHIPPING mới được xác nhận giao thành công");
        }
        if (COD.equalsIgnoreCase(order.getPaymentMethod())) {
            order.setPaymentStatus("PAID");
            order.setPaidAt(LocalDateTime.now());
            recordPayment(order, "PAYMENT", "COD-" + order.getOrderCode());
        }
        order.setStatus("DELIVERED");
        order.setDeliveryStatus("DELIVERED");
        dataSync.order(order, "ORDER_DELIVERED", "", BigDecimal.ZERO, Map.of());
        return mapToResponse(salesOrderRepository.save(order));
    }

    @Override
    @Transactional
    public com.api.supermarket.dto.response.SalesOrderReturnResponse returnSalesOrder(Long orderId, ReturnOrderRequest request) {
        SalesOrder order = loadOrderForUpdate(orderId);
        if (request == null || isBlank(request.getRequestKey()) || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Cần mã yêu cầu và ít nhất một mặt hàng trả");
        }
        String requestKey = request.getRequestKey().trim();
        SalesOrderReturn existing = salesOrderReturnRepository.findByRequestKey(requestKey).orElse(null);
        if (existing != null) {
            if (!existing.getOrderId().equals(orderId)) {
                throw new BadRequestException("Mã yêu cầu trả hàng đã được dùng cho đơn khác");
            }
            ensureSameReturnRequest(existing, request);
            return mapReturnResponse(existing);
        }
        boolean paidPos = IN_STORE.equals(order.getOrderType()) && "PAID".equals(order.getStatus());
        if (!paidPos && !Set.of("DELIVERED", "RETURN_IN_TRANSIT", "PARTIALLY_RETURNED").contains(order.getStatus())) {
            throw new BadRequestException("Chỉ đơn đã thanh toán tại quầy, đã giao hoặc đang nhận hàng hoàn mới được trả");
        }
        if (!order.isInventoryDeducted()) {
            throw new BadRequestException("Đơn chưa xuất kho nên không thể tạo phiếu trả hàng");
        }

        List<SalesOrderItem> orderItems = salesOrderItemRepository.findBySalesOrder_OrderId(orderId);
        Map<Long, SalesOrderItem> itemsById = new java.util.HashMap<>();
        orderItems.forEach(item -> itemsById.put(item.getOrderItemId(), item));
        Set<Long> requestedItemIds = new java.util.HashSet<>();
        for (ReturnItemRequest itemRequest : request.getItems()) {
            if (itemRequest == null || itemRequest.getOrderItemId() == null || itemRequest.getQuantity() == null
                    || itemRequest.getQuantity() <= 0 || itemRequest.getRestock() == null || isBlank(itemRequest.getReason())
                    || !requestedItemIds.add(itemRequest.getOrderItemId())) {
                throw new BadRequestException("Mặt hàng trả phải có mã, số lượng dương, tình trạng, lý do và không trùng");
            }
            SalesOrderItem orderItem = itemsById.get(itemRequest.getOrderItemId());
            if (orderItem == null) {
                throw new BadRequestException("Mặt hàng không thuộc đơn hàng này");
            }
            int alreadyReturned = orderItem.getReturnedQuantity() == null ? 0 : orderItem.getReturnedQuantity();
            try {
                com.api.supermarket.domain.ReturnPolicy.refundAmount(orderItem.getQuantity(), alreadyReturned,
                        itemRequest.getQuantity(), orderItem.getUnitPrice());
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException(ex.getMessage());
            }
        }

        SalesOrderReturn salesReturn = new SalesOrderReturn();
        salesReturn.setOrderId(orderId);
        salesReturn.setRequestKey(requestKey);
        salesReturn.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        salesReturn.setRefundStatus("NO_REFUND");
        List<SalesOrderReturnItem> returnItems = new ArrayList<>();
        BigDecimal refundAmount = BigDecimal.ZERO;
        List<ReturnItemRequest> sortedRequests = request.getItems().stream()
                .sorted(java.util.Comparator.comparing(item ->
                        itemsById.get(item.getOrderItemId()).getProduct().getProductId())).toList();
        for (ReturnItemRequest itemRequest : sortedRequests) {
            SalesOrderItem orderItem = itemsById.get(itemRequest.getOrderItemId());
            Product product = productRepository.findByIdForUpdate(orderItem.getProduct().getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm trả về"));
            int quantity = itemRequest.getQuantity();
            boolean restock = itemRequest.getRestock();
            inventoryBatchService.receiveReturn(orderItem, quantity, restock, itemRequest.getBatchId());
            if (restock) {
                product.setStockQuantity(Math.addExact(product.getStockQuantity(), quantity));
            } else {
                product.setDamagedQuantity(Math.addExact(product.getDamagedQuantity(), quantity));
            }
            productRepository.save(product);
            saveMovement(product, orderId, restock ? "RETURN" : "RETURN_DAMAGED", quantity,
                    itemRequest.getReason().trim());
            int returnedBefore = orderItem.getReturnedQuantity() == null ? 0 : orderItem.getReturnedQuantity();
            refundAmount = refundAmount.add(com.api.supermarket.domain.ReturnPolicy.refundAmount(
                    orderItem.getQuantity(), returnedBefore, quantity, orderItem.getUnitPrice()));
            orderItem.setReturnedQuantity(returnedBefore + quantity);

            SalesOrderReturnItem returnItem = new SalesOrderReturnItem();
            returnItem.setSalesOrderReturn(salesReturn);
            returnItem.setOrderItem(orderItem);
            returnItem.setBatchId(itemRequest.getBatchId());
            returnItem.setQuantity(quantity);
            returnItem.setRestock(restock);
            returnItem.setReason(itemRequest.getReason().trim());
            returnItems.add(returnItem);
        }
        boolean paymentWasCollected = Set.of("PAID", "PARTIALLY_REFUNDED", "REFUND_PENDING")
                .contains(order.getPaymentStatus());
        salesReturn.setRefundAmount(refundAmount);
        salesReturn.setRefundStatus(paymentWasCollected && refundAmount.signum() > 0 ? "REFUND_PENDING" : "NO_REFUND");
        SalesOrderReturn savedReturn = salesOrderReturnRepository.save(salesReturn);
        salesOrderReturnItemRepository.saveAll(returnItems);
        salesOrderItemRepository.saveAll(orderItems);
        boolean fullyReturned = orderItems.stream().allMatch(item -> item.getReturnedQuantity() != null
                && item.getReturnedQuantity() >= item.getQuantity());
        order.setInventoryReleased(fullyReturned);
        if (fullyReturned) {
            order.setStatus("RETURNED");
            order.setDeliveryStatus("RETURNED");
        } else {
            order.setStatus("PARTIALLY_RETURNED");
        }
        if (paymentWasCollected && refundAmount.signum() > 0) {
            order.setPaymentStatus("REFUND_PENDING");
        } else if (fullyReturned && !paymentWasCollected) {
            order.setPaymentStatus("CANCELLED");
        }
        salesOrderRepository.save(order);
        dataSync.order(order, "RETURN_RECEIVED", savedReturn.getReturnId().toString(), refundAmount,
                Map.of("returnId", savedReturn.getReturnId(), "refundStatus", savedReturn.getRefundStatus()));
        return mapReturnResponse(savedReturn);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderResponse getSalesOrderById(Long orderId) {
        SalesOrder order = loadOrder(orderId);
        ensureOrderAccess(order);
        return mapToResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderResponse getSalesOrderCode(String orderCode) {
        SalesOrder order = salesOrderRepository.findByOrderCodeIgnoreCase(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn với mã: " + orderCode));
        ensureOrderAccess(order);
        return mapToResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> getAllSalesOrders() {
        return filterSalesOrders(0, 10, "createdAt", "desc", null, null, null);
    }

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "orderId", "orderCode", "orderDate", "subtotal", "totalAmount", "status", "createdAt");

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> filterSalesOrders(int page, int size, String sortBy, String sortDir,
            String status, String paymentMethod, String searchTerm) {
        PaginationValidator.validate(page, size, sortBy, sortDir, ALLOWED_SORT_FIELDS);
        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<SalesOrder> orderPage = salesOrderRepository.filterSalesOrder(
                isBlank(searchTerm) ? null : searchTerm.trim(), isBlank(status) ? null : normalize(status), null,
                isBlank(paymentMethod) ? null : normalize(paymentMethod), null, null, pageable);
        List<SalesOrderResponse> content = orderPage.getContent().stream().map(this::mapToResponse).toList();
        return new PageResponse<>(content, orderPage.getNumber(), orderPage.getSize(), orderPage.getTotalElements(),
                orderPage.getTotalPages(), orderPage.isLast());
    }

    private SalesOrder loadOrder(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn với ID: " + orderId));
    }

    private SalesOrder loadOrderForUpdate(Long orderId) {
        return salesOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + orderId));
    }

    private void ensureOrderAccess(SalesOrder order) {
        if (isStaff()) {
            return;
        }
        User user = currentUser();
        boolean creator = user != null && order.getUser() != null
                && user.getUserId().equals(order.getUser().getUserId());
        boolean customerOwner = user != null && order.getCustomer() != null && order.getCustomer().getUser() != null
                && user.getUserId().equals(order.getCustomer().getUser().getUserId());
        if (!ONLINE.equals(order.getOrderType()) || (!creator && !customerOwner)) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền truy cập đơn hàng");
        }
    }

    private boolean isStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> Set.of("ROLE_ADMIN", "ROLE_STAFF").contains(a.getAuthority()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> getMyOnlineOrders(int page, int size) {
        PaginationValidator.validate(page, size, "createdAt", "desc", ALLOWED_SORT_FIELDS);
        User user = currentUser();
        if (user == null) {
            throw new com.api.supermarket.exception.UnauthorizedException("Bạn cần đăng nhập");
        }
        Page<SalesOrder> orders = salesOrderRepository.findMyOnlineOrders(user.getUserName(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return new PageResponse<>(orders.getContent().stream().map(this::mapToResponse).toList(),
                orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages(), orders.isLast());
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUserName(authentication.getName()).orElse(null);
    }

    private void validateOrderInput(String orderType, String paymentMethod, SalesOrderRequest request) {
        if (!IN_STORE.equals(orderType) && !ONLINE.equals(orderType)) {
            throw new BadRequestException("orderType chỉ được là IN_STORE hoặc ONLINE");
        }
        if (!Set.of(CASH, BANK_TRANSFER, COD).contains(paymentMethod)) {
            throw new BadRequestException("Phương thức thanh toán không được hỗ trợ");
        }
        if (IN_STORE.equals(orderType) && COD.equals(paymentMethod)) {
            throw new BadRequestException("Đơn tại cửa hàng không dùng COD");
        }
        if (ONLINE.equals(orderType) && CASH.equals(paymentMethod)) {
            throw new BadRequestException("Đơn ONLINE dùng BANK_TRANSFER hoặc COD (tiền mặt khi nhận hàng)");
        }
        if (IN_STORE.equals(orderType) && BANK_TRANSFER.equals(paymentMethod)
                && isBlank(request.getPaymentReference())) {
            throw new BadRequestException("Thanh toán chuyển khoản tại quầy cần mã giao dịch đã xác nhận thành công");
        }
        if (ONLINE.equals(orderType) && (isBlank(request.getShippingAddress())
                || isBlank(request.getReceiverName()) || isBlank(request.getReceiverPhone()))) {
            throw new BadRequestException("Đơn ONLINE phải có thông tin giao hàng");
        }
    }

    private Map<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("Đơn hàng phải có ít nhất một sản phẩm");
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        for (OrderItemRequest item : items) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Sản phẩm và số lượng trong đơn hàng phải hợp lệ");
            }
            try {
                quantities.merge(item.getProductId(), item.getQuantity(), Math::addExact);
            } catch (ArithmeticException ex) {
                throw new BadRequestException("Số lượng sản phẩm vượt giới hạn cho phép");
            }
        }
        return quantities;
    }

    private void releaseInventory(SalesOrder order, String reason) {
        releaseInventory(order, reason, true);
    }

    private void releaseInventory(SalesOrder order, String reason, boolean restock) {
        if (order.isInventoryReleased()) {
            return;
        }
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrder_OrderId(order.getOrderId());
        items.sort(Comparator.comparing(item -> item.getProduct().getProductId()));
        for (SalesOrderItem item : items) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm để hoàn kho"));
            if (order.isInventoryDeducted()) {
                inventoryBatchService.restoreCancelledStock(item);
                if (restock) {
                    product.setStockQuantity(Math.addExact(product.getStockQuantity(), item.getQuantity()));
                } else {
                    product.setDamagedQuantity(Math.addExact(product.getDamagedQuantity(), item.getQuantity()));
                }
            } else {
                inventoryBatchService.releaseReserved(item);
                if (product.getReservedQuantity() < item.getQuantity()) {
                    throw new BadRequestException("Số lượng giữ hàng không khớp với đơn hàng");
                }
                product.setReservedQuantity(product.getReservedQuantity() - item.getQuantity());
            }
            productRepository.save(product);
            String movementType = order.isInventoryDeducted() ? (restock ? "RETURN" : "RETURN_DAMAGED") : "RELEASE";
            saveMovement(product, order.getOrderId(), movementType,
                    item.getQuantity(), reason);
        }
        order.setInventoryReleased(true);
    }

    private void deductReservedInventory(SalesOrder order) {
        if (order.isInventoryReleased()) {
            throw new BadRequestException("Đơn đã hoàn kho, không thể xuất hàng");
        }
        if (order.isInventoryDeducted()) {
            return; // Đơn cũ đã trừ kho ngay lúc tạo.
        }
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrder_OrderId(order.getOrderId());
        items.sort(Comparator.comparing(item -> item.getProduct().getProductId()));
        for (SalesOrderItem item : items) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
            if (product.getReservedQuantity() < item.getQuantity() || product.getStockQuantity() < item.getQuantity()) {
                throw new InsufficientStockException("Không đủ hàng đã giữ để xuất kho");
            }
            if (!product.getIsActive()) {
                throw new BadRequestException("Hàng đã ngừng bán hoặc hết hạn, cần hủy đơn và xử lý hoàn tiền nếu đã thu");
            }
            inventoryBatchService.consumeReserved(item);
            product.setReservedQuantity(product.getReservedQuantity() - item.getQuantity());
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
            productRepository.save(product);
            saveMovement(product, order.getOrderId(), "SALE", -item.getQuantity(), "Xuất hàng cho vận chuyển");
        }
        order.setInventoryDeducted(true);
    }

    @Override
    @Transactional
    public SalesOrderResponse failDelivery(Long orderId) {
        SalesOrder order = loadOrderForUpdate(orderId);
        if ("RETURN_IN_TRANSIT".equals(order.getStatus())) {
            return mapToResponse(order);
        }
        if (!"SHIPPING".equals(order.getStatus())) {
            throw new BadRequestException("Chỉ đơn đang giao mới có thể báo giao thất bại");
        }
        order.setStatus("RETURN_IN_TRANSIT");
        order.setDeliveryStatus("RETURN_IN_TRANSIT");
        dataSync.order(order, "DELIVERY_FAILED", "", BigDecimal.ZERO, Map.of());
        return mapToResponse(salesOrderRepository.save(order));
    }

    @Override
    @Transactional
    public SalesOrderResponse confirmRefund(Long orderId, PaymentConfirmationRequest request) {
        SalesOrder order = loadOrderForUpdate(orderId);
        if (!salesOrderReturnRepository.findByOrderIdOrderByReturnIdAsc(orderId).isEmpty()) {
            throw new BadRequestException("Đơn có phiếu trả hàng: xác nhận hoàn tiền theo từng phiếu");
        }
        validatePaymentConfirmation(order, request);
        if ("REFUNDED".equals(order.getPaymentStatus())) {
            ensureMatchingPayment(order, "REFUND", request);
            return mapToResponse(order);
        }
        if (!"REFUND_PENDING".equals(order.getPaymentStatus())
                || !Set.of("CANCELLED", "RETURNED").contains(order.getStatus())) {
            throw new BadRequestException("Đơn không ở trạng thái chờ hoàn tiền");
        }
        order.setPaymentStatus("REFUNDED");
        recordPayment(order, "REFUND", request.getReference().trim());
        return mapToResponse(salesOrderRepository.save(order));
    }

    @Override
    @Transactional
    public com.api.supermarket.dto.response.SalesOrderReturnResponse confirmReturnRefund(
            Long returnId, PaymentConfirmationRequest request) {
        SalesOrderReturn originalReturn = salesOrderReturnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu trả hàng"));
        SalesOrder order = loadOrderForUpdate(originalReturn.getOrderId());
        SalesOrderReturn salesReturn = salesOrderReturnRepository.findByIdForUpdate(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu trả hàng"));
        if (request == null || isBlank(request.getReference()) || request.getAmount() == null
                || request.getAmount().compareTo(salesReturn.getRefundAmount()) != 0) {
            throw new BadRequestException("Số tiền hoàn phải khớp số tiền của phiếu trả hàng");
        }
        if ("REFUNDED".equals(salesReturn.getRefundStatus())) {
            ensureMatchingReturnRefund(salesReturn, request);
            return mapReturnResponse(salesReturn);
        }
        if (!"REFUND_PENDING".equals(salesReturn.getRefundStatus())) {
            throw new BadRequestException("Phiếu trả hàng này không có khoản tiền cần hoàn");
        }
        salesReturn.setRefundStatus("REFUNDED");
        salesReturn.setRefundedAt(LocalDateTime.now());
        recordPayment(order, "REFUND", request.getReference().trim(), returnId);
        salesOrderReturnRepository.save(salesReturn);

        BigDecimal refunded = salesOrderReturnRepository.sumRefundedByOrderId(order.getOrderId());
        boolean pending = salesOrderReturnRepository.existsByOrderIdAndRefundStatusIn(order.getOrderId(),
                List.of("REFUND_PENDING"));
        if (pending) {
            order.setPaymentStatus("REFUND_PENDING");
        } else if (refunded.compareTo(order.getTotalAmount()) >= 0) {
            order.setPaymentStatus("REFUNDED");
        } else if (refunded.signum() > 0) {
            order.setPaymentStatus("PARTIALLY_REFUNDED");
        }
        salesOrderRepository.save(order);
        dataSync.order(order, "RETURN_REFUND_CONFIRMED", returnId.toString(), BigDecimal.ZERO,
                Map.of("returnId", returnId));
        return mapReturnResponse(salesReturn);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.api.supermarket.dto.response.SalesOrderReturnResponse> getReturns(Long orderId) {
        ensureOrderAccess(loadOrder(orderId));
        return salesOrderReturnRepository.findByOrderIdOrderByReturnIdAsc(orderId).stream()
                .map(this::mapReturnResponse).toList();
    }

    private com.api.supermarket.dto.response.SalesOrderReturnResponse mapReturnResponse(SalesOrderReturn salesReturn) {
        List<com.api.supermarket.dto.response.SalesOrderReturnResponse.Item> items =
                salesOrderReturnItemRepository.findBySalesOrderReturn_ReturnIdOrderByReturnItemIdAsc(salesReturn.getReturnId())
                        .stream().map(item -> new com.api.supermarket.dto.response.SalesOrderReturnResponse.Item(
                                item.getOrderItem().getOrderItemId(), item.getOrderItem().getProduct().getProductId(), item.getBatchId(),
                                item.getOrderItem().getProduct().getProductName(), item.getQuantity(), item.isRestock(),
                                item.getReason())).toList();
        return new com.api.supermarket.dto.response.SalesOrderReturnResponse(salesReturn.getReturnId(),
                salesReturn.getOrderId(), salesReturn.getRequestKey(), salesReturn.getRefundAmount(),
                salesReturn.getRefundStatus(), salesReturn.getCreatedAt(), items);
    }

    private void ensureSameReturnRequest(SalesOrderReturn salesReturn, ReturnOrderRequest request) {
        List<SalesOrderReturnItem> recorded = salesOrderReturnItemRepository
                .findBySalesOrderReturn_ReturnIdOrderByReturnItemIdAsc(salesReturn.getReturnId());
        Map<Long, ReturnItemRequest> incoming = new java.util.HashMap<>();
        for (ReturnItemRequest item : request.getItems()) {
            if (item == null || item.getOrderItemId() == null || item.getQuantity() == null
                    || item.getRestock() == null || isBlank(item.getReason())
                    || incoming.put(item.getOrderItemId(), item) != null) {
                throw new BadRequestException("Yêu cầu trả hàng lặp không hợp lệ");
            }
        }
        if (incoming.size() != recorded.size()) {
            throw new BadRequestException("Mã yêu cầu đã được dùng với nội dung trả hàng khác");
        }
        for (SalesOrderReturnItem item : recorded) {
            ReturnItemRequest candidate = incoming.get(item.getOrderItem().getOrderItemId());
            if (candidate == null || !candidate.getQuantity().equals(item.getQuantity())
                    || !java.util.Objects.equals(candidate.getBatchId(), item.getBatchId())
                    || candidate.getRestock() != item.isRestock()
                    || !candidate.getReason().trim().equals(item.getReason())) {
                throw new BadRequestException("Mã yêu cầu đã được dùng với nội dung trả hàng khác");
            }
        }
    }

    private void validatePaymentConfirmation(SalesOrder order, PaymentConfirmationRequest request) {
        if (request == null || isBlank(request.getReference()) || request.getAmount() == null
                || request.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new BadRequestException("Cần mã giao dịch/biên nhận và số tiền bằng tổng tiền của đơn hàng");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.api.supermarket.dto.response.PaymentRecordResponse> getPaymentRecords(Long orderId) {
        ensureOrderAccess(loadOrder(orderId));
        return paymentRecordRepository.findByOrderIdOrderByPaymentRecordIdAsc(orderId).stream()
                .map(p -> new com.api.supermarket.dto.response.PaymentRecordResponse(p.getPaymentRecordId(), p.getOrderId(), p.getReturnId(),
                        p.getEventType(), p.getReference(), p.getAmount(), p.getPaymentMethod(), p.getConfirmedBy(), p.getCreatedAt()))
                .toList();
    }

    private void ensureMatchingPayment(SalesOrder order, String type, PaymentConfirmationRequest request) {
        PaymentRecord payment = paymentRecordRepository.findFirstByOrderIdAndEventTypeOrderByPaymentRecordIdDesc(order.getOrderId(), type)
                .orElseThrow(() -> new BadRequestException("Đơn cũ chưa có biên nhận để đối soát yêu cầu lặp"));
        if (!payment.getReference().equals(request.getReference().trim())) {
            throw new BadRequestException("Đơn đã được xác nhận bằng một mã giao dịch khác");
        }
    }

    private void ensureMatchingReturnRefund(SalesOrderReturn salesReturn, PaymentConfirmationRequest request) {
        PaymentRecord payment = paymentRecordRepository.findByReturnIdAndEventType(salesReturn.getReturnId(), "REFUND")
                .orElseThrow(() -> new BadRequestException("Phiếu trả hàng chưa có biên nhận hoàn tiền"));
        if (!payment.getReference().equals(request.getReference().trim())) {
            throw new BadRequestException("Phiếu trả hàng đã hoàn bằng mã giao dịch khác");
        }
    }

    private void recordPayment(SalesOrder order, String type, String reference) {
        recordPayment(order, type, reference, null);
    }

    private void recordPayment(SalesOrder order, String type, String reference, Long returnId) {
        if (paymentRecordRepository.existsByReference(reference)) {
            throw new BadRequestException("Mã giao dịch/biên nhận đã được sử dụng");
        }
        PaymentRecord payment = new PaymentRecord();
        payment.setOrderId(order.getOrderId());
        payment.setReturnId(returnId);
        payment.setEventType(type);
        payment.setReference(reference);
        payment.setAmount(returnId == null ? order.getTotalAmount()
                : salesOrderReturnRepository.findById(returnId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phiếu trả hàng"))
                        .getRefundAmount());
        payment.setPaymentMethod(order.getPaymentMethod());
        User user = currentUser();
        payment.setConfirmedBy(user == null ? null : user.getUserId());
        dataSync.payment(paymentRecordRepository.save(payment));
    }

    @Override
    @Transactional
    public void expireUnpaidOrder(Long orderId) {
        SalesOrder order = loadOrderForUpdate(orderId);
        if (!"PENDING_PAYMENT".equals(order.getStatus()) || order.getPaymentExpiresAt() == null
                || order.getPaymentExpiresAt().isAfter(LocalDateTime.now())) {
            return;
        }
        releaseInventory(order, "PAYMENT_EXPIRED");
        order.setStatus("CANCELLED");
        order.setPaymentStatus("EXPIRED");
        order.setDeliveryStatus("CANCELLED");
        dataSync.order(order, "ORDER_EXPIRED", "", BigDecimal.ZERO, Map.of());
        salesOrderRepository.save(order);
    }

    private void saveMovement(Product product, Long orderId, String type, int quantity, String note) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setOrderId(orderId);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setNote(note);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        movement.setPerformedBy(authentication == null ? "SYSTEM" : authentication.getName());
        dataSync.movement(stockMovementRepository.save(movement));
    }

    private SalesOrderResponse mapToResponse(SalesOrder order) {
        List<SalesOrderItem> items = salesOrderItemRepository.findBySalesOrder_OrderId(order.getOrderId());
        return mapToResponse(order, items);
    }

    private SalesOrderResponse mapToResponse(SalesOrder order, List<SalesOrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream().map(item -> {
            Product p = item.getProduct();
            ProductResponse productResponse = new ProductResponse(p.getProductId(), p.getProductName(), p.getSkuCode(),
                    p.getPrice(), p.getStockQuantity(), p.getReorderPoint(), p.getExpiryDate(), p.getImageUrl(),
                    p.getIsActive(), p.getCategory() == null ? null : p.getCategory().getCategoryId(),
                    p.getCategory() == null ? null : p.getCategory().getCategoryName(),
                    p.getSupplier() == null ? null : p.getSupplier().getSupplierId(),
                    p.getSupplier() == null ? null : p.getSupplier().getSupplierName(), p.getCreateAt());
            productResponse.setReservedQuantity(p.getReservedQuantity());
            productResponse.setDamagedQuantity(p.getDamagedQuantity());
            productResponse.setAvailableQuantity(p.getIsActive() ? inventoryBatchService.usableQuantity(p.getProductId()) : 0);
            OrderItemResponse itemResponse = new OrderItemResponse(item.getOrderItemId(), productResponse, item.getQuantity(), item.getUnitPrice(),
                    item.getSubtotal());
            itemResponse.setReturnedQuantity(item.getReturnedQuantity());
            itemResponse.setBatchAllocations(inventoryBatchService.getAllocations(item.getOrderItemId()));
            return itemResponse;
        }).toList();

        Customer customer = order.getCustomer();
        User user = order.getUser();
        SalesOrderResponse response = new SalesOrderResponse(order.getOrderId(), order.getOrderCode(),
                customer == null ? null : customer.getCustomerId(), customer == null ? "Khách vãng lai" : customer.getFullName(),
                customer == null ? null : customer.getPhone(), user == null || ONLINE.equals(order.getOrderType()) ? null : user.getUserId(),
                user == null || ONLINE.equals(order.getOrderType()) ? null : user.getFullName(), order.getOrderType(), order.getPaymentMethod(), order.getStatus(),
                order.getNote(), order.getSubtotal(), order.getDiscountAmount(), order.getTotalAmount(), order.getCreatedAt(),
                order.getPaidAt(), order.getPaymentStatus(), order.getDeliveryStatus(), order.getShippingAddress(),
                order.getReceiverName(), order.getReceiverPhone(), itemResponses);
        response.setPaymentExpiresAt(order.getPaymentExpiresAt());
        return response;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
