package com.api.supermarket.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProductResponse {
    private Long productId;
    private String productName;
    private String skuCode;
    private BigDecimal price;
    private Integer stockQuantity;
    private Integer reservedQuantity = 0;
    private Integer damagedQuantity = 0;
    private Integer availableQuantity;
    private Integer reorderPoint;
    private LocalDate expiryDate;
    private String imageUrl;
    private Boolean isActive;
    private Long categoryId;
    private String categoryName;
    private Long supplierId;
    private String supplierName;
    private LocalDateTime createAt;


    public ProductResponse(
        Long productId,
        String productName,
        String skuCode,
        BigDecimal price,
        Integer stockQuantity,
        Integer reorderPoint,
        LocalDate expiryDate,
        String imageUrl,
        Boolean isActive,
        Long categoryId,
        String categoryName,
        Long supplierId,
        String supplierName,
        LocalDateTime createAt
    ) {
        this.productId = productId;
        this.productName = productName;
        this.skuCode = skuCode;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.reorderPoint = reorderPoint;
        this.expiryDate = expiryDate;
        this.imageUrl = imageUrl;
        this.isActive = isActive;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.createAt = createAt;
    }

    // Getters and Setters
    public Integer getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public Integer getAvailableQuantity() { return availableQuantity == null ? stockQuantity - reservedQuantity : availableQuantity; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
    public Integer getDamagedQuantity() { return damagedQuantity; }
    public void setDamagedQuantity(Integer damagedQuantity) { this.damagedQuantity = damagedQuantity; }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }



    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Integer getReorderPoint() {
        return reorderPoint;
    }

    public void setReorderPoint(Integer reorderPoint) {
        this.reorderPoint = reorderPoint;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

}
