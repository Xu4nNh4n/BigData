package com.api.supermarket.dto.request;

import jakarta.validation.constraints.NotBlank;

public class CreateCartRequest {
    @NotBlank
    private String cartType;
    private Long customerId;

    public String getCartType() { return cartType; }
    public void setCartType(String cartType) { this.cartType = cartType; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
}
