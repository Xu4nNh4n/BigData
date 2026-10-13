package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.api.supermarket.dto.request.CartItemRequest;
import com.api.supermarket.dto.request.CheckoutCartRequest;
import com.api.supermarket.dto.request.CreateCartRequest;
import com.api.supermarket.dto.response.CartResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;
import com.api.supermarket.entity.Cart;
import com.api.supermarket.entity.CartItem;
import com.api.supermarket.entity.Customer;
import com.api.supermarket.entity.Product;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.CartItemRepository;
import com.api.supermarket.repository.CartRepository;
import com.api.supermarket.repository.CustomerRepository;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.UserRepository;
import com.api.supermarket.service.InventoryBatchService;
import com.api.supermarket.service.SalesOrderService;

@ExtendWith(MockitoExtension.class)
public class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SalesOrderService salesOrderService;

    @Mock
    private InventoryBatchService inventoryBatchService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createSampleUser(Long id, String username) {
        User user = new User();
        user.setUserId(id);
        user.setUserName(username);
        return user;
    }

    private Customer createSampleCustomer(Long id, User user) {
        Customer customer = new Customer();
        customer.setCustomerId(id);
        customer.setUser(user);
        customer.setIsActive(true);
        return customer;
    }

    private Product createSampleProduct(Long id, String name, int stock, int reserved, BigDecimal price) {
        Product product = new Product();
        product.setProductId(id);
        product.setProductName(name);
        product.setStockQuantity(stock);
        product.setReservedQuantity(reserved);
        product.setPrice(price);
        product.setIsActive(true);
        return product;
    }

    @Test
    void createCart_InStore_Staff_Success() {
        User staff = createSampleUser(1L, "cashier");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("cashier");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
        when(userRepository.findByUserName("cashier")).thenReturn(Optional.of(staff));

        CreateCartRequest req = new CreateCartRequest();
        req.setCartType("IN_STORE");

        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> {
            Cart c = i.getArgument(0);
            c.setCartId(10L);
            c.setItems(new ArrayList<>());
            return c;
        });

        CartResponse response = cartService.createCart(req);

        assertNotNull(response);
        assertEquals(10L, response.getCartId());
        assertEquals("IN_STORE", response.getCartType());
    }

    @Test
    void createCart_InStore_NonStaff_ThrowsBadRequest() {
        User customerUser = createSampleUser(2L, "customer1");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("customer1");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userRepository.findByUserName("customer1")).thenReturn(Optional.of(customerUser));

        CreateCartRequest req = new CreateCartRequest();
        req.setCartType("IN_STORE");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> cartService.createCart(req));
        assertEquals("Chỉ nhân viên mới được mở giỏ tại cửa hàng", ex.getMessage());
    }

    @Test
    void createCart_Online_Customer_Success() {
        User user = createSampleUser(2L, "customer1");
        Customer customer = createSampleCustomer(5L, user);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("customer1");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userRepository.findByUserName("customer1")).thenReturn(Optional.of(user));
        when(customerRepository.findByUser_UserId(2L)).thenReturn(Optional.of(customer));

        CreateCartRequest req = new CreateCartRequest();
        req.setCartType("ONLINE");
        req.setCustomerId(5L);

        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> {
            Cart c = i.getArgument(0);
            c.setCartId(20L);
            c.setItems(new ArrayList<>());
            return c;
        });

        CartResponse response = cartService.createCart(req);

        assertNotNull(response);
        assertEquals(20L, response.getCartId());
        assertEquals("ONLINE", response.getCartType());
        assertEquals(5L, response.getCustomerId());
    }

    @Test
    void addItem_Success() {
        User user = createSampleUser(2L, "customer1");
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setStatus("OPEN");
        cart.setUser(user);
        cart.setItems(new ArrayList<>());

        Product product = createSampleProduct(100L, "Trà sữa", 50, 0, new BigDecimal("25000.00"));

        when(cartRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("customer1");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userRepository.findByUserName("customer1")).thenReturn(Optional.of(user));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(inventoryBatchService.usableQuantity(100L)).thenReturn(50);
        when(cartRepository.save(cart)).thenReturn(cart);

        CartItemRequest itemReq = new CartItemRequest();
        itemReq.setProductId(100L);
        itemReq.setQuantity(3);

        CartResponse response = cartService.addItem(1L, itemReq);

        assertNotNull(response);
        assertEquals(1, cart.getItems().size());
        assertEquals(3, cart.getItems().get(0).getQuantity());
        verify(cartRepository).save(cart);
    }

    @Test
    void addItem_ExceedsStock_ThrowsBadRequest() {
        User user = createSampleUser(2L, "customer1");
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setStatus("OPEN");
        cart.setUser(user);
        cart.setItems(new ArrayList<>());

        Product product = createSampleProduct(100L, "Trà sữa", 5, 2, new BigDecimal("25000.00")); // Available = 3

        when(cartRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("customer1");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userRepository.findByUserName("customer1")).thenReturn(Optional.of(user));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(inventoryBatchService.usableQuantity(100L)).thenReturn(3);

        CartItemRequest itemReq = new CartItemRequest();
        itemReq.setProductId(100L);
        itemReq.setQuantity(4); // 4 > 3 available

        BadRequestException ex = assertThrows(BadRequestException.class, () -> cartService.addItem(1L, itemReq));
        assertEquals("Số lượng trong giỏ vượt tồn kho hiện tại", ex.getMessage());
    }

    @Test
    void updateItem_Success() {
        User user = createSampleUser(2L, "customer1");
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setStatus("OPEN");
        cart.setUser(user);

        Product product = createSampleProduct(100L, "Bánh bao", 10, 0, new BigDecimal("15000.00"));
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(2);
        cart.getItems().add(item);

        when(cartRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("customer1");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userRepository.findByUserName("customer1")).thenReturn(Optional.of(user));
        when(inventoryBatchService.usableQuantity(100L)).thenReturn(10);
        when(cartRepository.save(cart)).thenReturn(cart);

        CartItemRequest updateReq = new CartItemRequest();
        updateReq.setProductId(100L);
        updateReq.setQuantity(5);

        CartResponse response = cartService.updateItem(1L, 100L, updateReq);

        assertNotNull(response);
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    void removeItem_Success() {
        User user = createSampleUser(2L, "customer1");
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setStatus("OPEN");
        cart.setUser(user);

        Product product = createSampleProduct(100L, "Bánh bao", 10, 0, new BigDecimal("15000.00"));
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(2);
        cart.getItems().add(item);

        when(cartRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("customer1");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userRepository.findByUserName("customer1")).thenReturn(Optional.of(user));
        when(cartRepository.save(cart)).thenReturn(cart);

        CartResponse response = cartService.removeItem(1L, 100L);

        assertNotNull(response);
        assertEquals(0, cart.getItems().size());
    }

    @Test
    void cancelCart_Success() {
        User user = createSampleUser(2L, "customer1");
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setStatus("OPEN");
        cart.setUser(user);

        when(cartRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("customer1");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userRepository.findByUserName("customer1")).thenReturn(Optional.of(user));

        cartService.cancelCart(1L);

        assertEquals("CANCELLED", cart.getStatus());
        verify(cartRepository).save(cart);
    }

    @Test
    void checkout_Success() {
        User user = createSampleUser(2L, "customer1");
        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setStatus("OPEN");
        cart.setCartType("ONLINE");
        cart.setUser(user);

        Product product = createSampleProduct(100L, "Trà xanh", 20, 0, new BigDecimal("10000.00"));
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(2);
        cart.getItems().add(item);

        when(cartRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(cart));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("customer1");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userRepository.findByUserName("customer1")).thenReturn(Optional.of(user));

        SalesOrderResponse orderResponse = new SalesOrderResponse();
        orderResponse.setOrderId(99L);
        when(salesOrderService.checkoutCart(eq(cart), any())).thenReturn(orderResponse);

        CheckoutCartRequest req = new CheckoutCartRequest();
        req.setPaymentMethod("COD");
        req.setShippingAddress("123 Phố Huế");
        req.setReceiverName("Nguyễn Văn A");
        req.setReceiverPhone("0987654321");

        SalesOrderResponse result = cartService.checkout(1L, req);

        assertNotNull(result);
        assertEquals(99L, result.getOrderId());
        assertEquals("CHECKED_OUT", cart.getStatus());
        assertEquals(99L, cart.getOrderId());
        verify(cartRepository).save(cart);
    }

    @Test
    void getCart_UnauthorizedUser_ThrowsAccessDenied() {
        User owner = createSampleUser(2L, "owner");
        User otherUser = createSampleUser(3L, "other");

        Cart cart = new Cart();
        cart.setCartId(1L);
        cart.setUser(owner);
        cart.setItems(new ArrayList<>());

        when(cartRepository.findWithItemsByCartId(1L)).thenReturn(Optional.of(cart));
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("other");
        when(authentication.getAuthorities()).thenAnswer(i -> List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        when(userRepository.findByUserName("other")).thenReturn(Optional.of(otherUser));

        assertThrows(AccessDeniedException.class, () -> cartService.getCart(1L));
    }
}
