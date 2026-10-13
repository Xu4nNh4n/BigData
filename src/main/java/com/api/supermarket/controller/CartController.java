package com.api.supermarket.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api.supermarket.dto.request.CartItemRequest;
import com.api.supermarket.dto.request.CheckoutCartRequest;
import com.api.supermarket.dto.request.CreateCartRequest;
import com.api.supermarket.dto.response.CartResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;
import com.api.supermarket.service.CartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/carts")
@Tag(name = "Shopping Cart - Giỏ hàng", description = "APIs for shopping carts (POS in-store & customer online checkout) - Các API quản lý giỏ hàng tại quầy POS và trực tuyến")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    @Operation(summary = "Create cart - Tạo giỏ hàng mới", description = "Create a shopping cart for IN_STORE or ONLINE purchases - Tạo giỏ hàng mới phục vụ mua sắm tại cửa hàng hoặc online")
    public ResponseEntity<CartResponse> createCart(@Valid @RequestBody CreateCartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cartService.createCart(request));
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Add item to cart - Thêm sản phẩm vào giỏ hàng", description = "Add a product with quantity into the specified cart - Thêm sản phẩm với số lượng mong muốn vào giỏ hàng")
    public ResponseEntity<CartResponse> addItem(@PathVariable Long id, @Valid @RequestBody CartItemRequest request) {
        return ResponseEntity.ok(cartService.addItem(id, request));
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "Checkout cart - Thanh toán giỏ hàng", description = "Checkout items in cart to create a sales order - Thực hiện thanh toán giỏ hàng và chuyển thành hóa đơn mua hàng")
    public ResponseEntity<SalesOrderResponse> checkout(@PathVariable Long id,
            @Valid @RequestBody CheckoutCartRequest request) {
        return ResponseEntity.ok(cartService.checkout(id, request));
    }
}
