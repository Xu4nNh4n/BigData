package com.api.supermarket.dto.response;

import java.math.BigDecimal;
import java.util.List;

public class CartResponse {
    private Long cartId;
    private String cartType;
    private String status;
    private Long orderId;
    private Long customerId;
    private BigDecimal totalAmount;
    private List<CartItemResponse> items;

    public CartResponse(Long cartId, String cartType, String status, Long customerId,
            BigDecimal totalAmount, List<CartItemResponse> items) {
        this.cartId = cartId;
        this.cartType = cartType;
        this.status = status;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.items = items;
    }

    public Long getCartId() { return cartId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getCartType() { return cartType; }
    public String getStatus() { return status; }
    public Long getCustomerId() { return customerId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public List<CartItemResponse> getItems() { return items; }
}
