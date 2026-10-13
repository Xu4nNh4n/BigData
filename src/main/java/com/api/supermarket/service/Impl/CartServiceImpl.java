package com.api.supermarket.service.Impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.supermarket.dto.request.CartItemRequest;
import com.api.supermarket.dto.request.CheckoutCartRequest;
import com.api.supermarket.dto.request.CreateCartRequest;
import com.api.supermarket.dto.request.OrderItemRequest;
import com.api.supermarket.dto.request.SalesOrderRequest;
import com.api.supermarket.dto.response.CartItemResponse;
import com.api.supermarket.dto.response.CartResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;
import com.api.supermarket.entity.Cart;
import com.api.supermarket.entity.CartItem;
import com.api.supermarket.entity.Customer;
import com.api.supermarket.entity.Product;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.CartRepository;
import com.api.supermarket.repository.CustomerRepository;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.UserRepository;
import com.api.supermarket.service.CartService;
import com.api.supermarket.service.SalesOrderService;

@Service
public class CartServiceImpl implements CartService {
    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SalesOrderService salesOrderService;
    private final com.api.supermarket.service.InventoryBatchService inventoryBatchService;

    public CartServiceImpl(CartRepository cartRepository, CustomerRepository customerRepository,
            ProductRepository productRepository, UserRepository userRepository,
            SalesOrderService salesOrderService, com.api.supermarket.service.InventoryBatchService inventoryBatchService) {
        this.cartRepository = cartRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.salesOrderService = salesOrderService;
        this.inventoryBatchService = inventoryBatchService;
    }

    @Override
    @Transactional
    public CartResponse createCart(CreateCartRequest request) {
        if (request == null) {
            throw new BadRequestException("Dữ liệu giỏ hàng không được để trống");
        }
        String cartType = normalize(request.getCartType());
        if (!cartType.equals("IN_STORE") && !cartType.equals("ONLINE")) {
            throw new BadRequestException("cartType chỉ được là IN_STORE hoặc ONLINE");
        }

        Cart cart = new Cart();
        cart.setCartType(cartType);
        cart.setUser(currentUser());

        if (cartType.equals("ONLINE") && !isCustomer()) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ tài khoản CUSTOMER được mua hàng online");
        }

        if (cartType.equals("IN_STORE") && !isStaff()) {
            throw new BadRequestException("Chỉ nhân viên mới được mở giỏ tại cửa hàng");
        }
        if (cartType.equals("ONLINE") && cart.getUser() == null) {
            throw new BadRequestException("Giỏ ONLINE phải gắn với tài khoản đăng nhập");
        }

        if (cartType.equals("ONLINE")) {
            Customer customer = customerRepository.findByUser_UserId(cart.getUser().getUserId())
                    .orElseThrow(() -> new BadRequestException("Tài khoản chưa có hồ sơ khách hàng"));
            if (request.getCustomerId() != null && !request.getCustomerId().equals(customer.getCustomerId())) {
                throw new org.springframework.security.access.AccessDeniedException("Không thể dùng hồ sơ khách hàng khác");
            }
            cart.setCustomer(customer);
        } else if (request.getCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng"));
            cart.setCustomer(customer);
        }
        if (cart.getCustomer() != null && !Boolean.TRUE.equals(cart.getCustomer().getIsActive())) {
            throw new BadRequestException("Hồ sơ khách hàng đã bị khóa");
        }

        return toResponse(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public CartResponse addItem(Long cartId, CartItemRequest request) {
        Cart cart = loadCartForUpdate(cartId);
        ensureAccess(cart);
        ensureOpen(cart);
        if (request == null || request.getProductId() == null || request.getQuantity() == null
                || request.getQuantity() <= 0) {
            throw new BadRequestException("Sản phẩm và số lượng trong giỏ hàng phải hợp lệ");
        }
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        if (!product.getIsActive()) {
            throw new BadRequestException("Sản phẩm đã ngừng kinh doanh");
        }

        CartItem item = cart.getItems().stream()
                .filter(existing -> existing.getProduct().getProductId().equals(product.getProductId()))
                .findFirst().orElse(null);
        long combinedQuantity = (long) request.getQuantity() + (item == null ? 0 : item.getQuantity());
        if (combinedQuantity > inventoryBatchService.usableQuantity(product.getProductId())) {
            throw new BadRequestException("Số lượng trong giỏ vượt tồn kho hiện tại");
        }
        int newQuantity = (int) combinedQuantity;
        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            cart.getItems().add(item);
        }
        item.setQuantity(newQuantity);
        return toResponse(cartRepository.save(cart));
    }

    @Override
    @Transactional
    public SalesOrderResponse checkout(Long cartId, CheckoutCartRequest request) {
        Cart cart = loadCartForUpdate(cartId);
        ensureAccess(cart);
        if ("CHECKED_OUT".equals(cart.getStatus()) && cart.getOrderId() != null) {
            return salesOrderService.getSalesOrderById(cart.getOrderId());
        }
        ensureOpen(cart);
        if (request == null) {
            throw new BadRequestException("Dữ liệu thanh toán không được để trống");
        }
        if (cart.getItems().isEmpty()) {
            throw new BadRequestException("Không thể thanh toán giỏ hàng rỗng");
        }

        SalesOrderRequest orderRequest = new SalesOrderRequest();
        orderRequest.setCustomerId(cart.getCustomer() == null ? null : cart.getCustomer().getCustomerId());
        orderRequest.setOrderType(cart.getCartType());
        orderRequest.setPaymentMethod(request.getPaymentMethod());
        orderRequest.setPaymentReference(request.getPaymentReference());
        orderRequest.setNote(request.getNote());
        orderRequest.setShippingAddress(request.getShippingAddress());
        orderRequest.setReceiverName(request.getReceiverName());
        orderRequest.setReceiverPhone(request.getReceiverPhone());

        var items = new ArrayList<OrderItemRequest>();
        for (CartItem cartItem : cart.getItems()) {
            OrderItemRequest item = new OrderItemRequest();
            item.setProductId(cartItem.getProduct().getProductId());
            item.setQuantity(cartItem.getQuantity());
            items.add(item);
        }
        orderRequest.setItems(items);

        SalesOrderResponse response = salesOrderService.checkoutCart(cart, orderRequest);
        cart.setStatus("CHECKED_OUT");
        cart.setOrderId(response.getOrderId());
        cartRepository.save(cart);
        return response;
    }

    private Cart loadCartForUpdate(Long cartId) {
        return cartRepository.findByIdForUpdate(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giỏ hàng"));
    }

    private void ensureOpen(Cart cart) {
        if (!"OPEN".equals(cart.getStatus())) {
            throw new BadRequestException("Giỏ hàng không còn ở trạng thái OPEN");
        }
    }

    private void ensureAccess(Cart cart) {
        if ("ONLINE".equals(cart.getCartType()) && !isCustomer()) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ tài khoản CUSTOMER được dùng giỏ online");
        }
        if (!"ONLINE".equals(cart.getCartType()) && isStaff()) {
            return;
        }
        User user = currentUser();
        if (user == null || cart.getUser() == null || !user.getUserId().equals(cart.getUser().getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền truy cập giỏ hàng");
        }
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUserName(authentication.getName()).orElse(null);
    }

    private boolean isStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_STAFF"));
    }

    private boolean isCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private CartResponse toResponse(Cart cart) {
        var items = cart.getItems().stream().map(item -> {
            Product product = item.getProduct();
            BigDecimal price = product.getPrice();
            return new CartItemResponse(product.getProductId(), product.getProductName(), product.getSkuCode(),
                    item.getQuantity(), price, price.multiply(BigDecimal.valueOf(item.getQuantity())));
        }).toList();
        BigDecimal total = items.stream().map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        CartResponse response = new CartResponse(cart.getCartId(), cart.getCartType(), cart.getStatus(),
                cart.getCustomer() == null ? null : cart.getCustomer().getCustomerId(), total, items);
        response.setOrderId(cart.getOrderId());
        return response;
    }
}
