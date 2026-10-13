package com.api.supermarket.dto.response;

import java.math.BigDecimal;

public class CartItemResponse {
    private Long productId;
    private String productName;
    private String skuCode;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    public CartItemResponse(Long productId, String productName, String skuCode, Integer quantity,
            BigDecimal unitPrice, BigDecimal subtotal) {
        this.productId = productId;
        this.productName = productName;
        this.skuCode = skuCode;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getSkuCode() { return skuCode; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getSubtotal() { return subtotal; }
}
