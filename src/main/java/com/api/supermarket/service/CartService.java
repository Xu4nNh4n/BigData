package com.api.supermarket.service;

import com.api.supermarket.dto.request.CartItemRequest;
import com.api.supermarket.dto.request.CheckoutCartRequest;
import com.api.supermarket.dto.request.CreateCartRequest;
import com.api.supermarket.dto.response.CartResponse;
import com.api.supermarket.dto.response.SalesOrderResponse;

public interface CartService {
    CartResponse createCart(CreateCartRequest request);
    CartResponse addItem(Long cartId, CartItemRequest request);
    SalesOrderResponse checkout(Long cartId, CheckoutCartRequest request);
}
