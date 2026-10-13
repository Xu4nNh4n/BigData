package com.api.supermarket.mongodb.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "products_catalog")
public class ProductCatalogDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    private String name;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal price;
    private Long productId;
    private Long categoryId;
    private Long supplierId;
    private String imageUrl;
    private Instant syncedAt;
    private String categoryName;
    private String supplierName;
    private boolean active;
    private Map<String, Object> attributes;
    private List<BatchSnapshot> batches;

    public ProductCatalogDocument() {
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long value) { productId = value; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { categoryId = value; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long value) { supplierId = value; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String value) { imageUrl = value; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant value) { syncedAt = value; }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public List<BatchSnapshot> getBatches() {
        return batches;
    }

    public void setBatches(List<BatchSnapshot> batches) {
        this.batches = batches;
    }

    public static class BatchSnapshot {
        private String batchCode;
        private int quantity;
        private Instant expiryDate;
        private int reservedQuantity;
        private int damagedQuantity;
        private String status;

        public int getReservedQuantity() { return reservedQuantity; }
        public void setReservedQuantity(int value) { reservedQuantity = value; }
        public int getDamagedQuantity() { return damagedQuantity; }
        public void setDamagedQuantity(int value) { damagedQuantity = value; }
        public String getStatus() { return status; }
        public void setStatus(String value) { status = value; }

        public BatchSnapshot() {
        }

        public String getBatchCode() {
            return batchCode;
        }

        public void setBatchCode(String batchCode) {
            this.batchCode = batchCode;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public Instant getExpiryDate() {
            return expiryDate;
        }

        public void setExpiryDate(Instant expiryDate) {
            this.expiryDate = expiryDate;
        }
    }
}
