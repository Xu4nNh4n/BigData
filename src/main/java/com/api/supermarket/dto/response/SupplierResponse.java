package com.api.supermarket.dto.response;

import java.time.LocalDateTime;

public class SupplierResponse {
    private Long supplierId;
    private String supplierName;
    private String supplierPhone;
    private String supplierEmail;
    private String supplierAddress;
    private Boolean isActive;
    private LocalDateTime createAt;

    public SupplierResponse(Long supplierId, String supplierName, String supplierPhone, String supplierEmail, String supplierAddress, Boolean isActive, LocalDateTime createAt) {
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.supplierPhone = supplierPhone;
        this.supplierEmail = supplierEmail;
        this.supplierAddress = supplierAddress;
        this.isActive = isActive;
        this.createAt = createAt;
    
    }

    //getters and setters
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

    public String getSupplierPhone() {
        return supplierPhone;
    }

    public void setSupplierPhone(String supplierPhone) {
        this.supplierPhone = supplierPhone;
    }

    public String getSupplierEmail() {
        return supplierEmail;
    }

    public void setSupplierEmail(String supplierEmail) {
        this.supplierEmail = supplierEmail;
    }

    public String getSupplierAddress() {
        return supplierAddress;
    }



    public void setSupplierAddress(String supplierAddress) {
        this.supplierAddress = supplierAddress;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }
    
}
