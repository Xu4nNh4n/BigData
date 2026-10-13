package com.api.supermarket.dto.request;

import jakarta.validation.constraints.*;

public class SupplierRequest {
    @NotBlank(message = "Tên nhà cung cấp không được để trống")
    @Size(max = 150, message = "Tên nhà cung cấp tối đa 150 kí tự")
    private String supplierName;

    @Size(max = 20, message = "Số điện thoại tối đa 20 kí tự")
    private String supplierPhone;

    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email nhà cung cấp tối đa 100 kí tự")
    private String supplierEmail;

    @Size(max = 255, message = "Địa chỉ nhà cung cấp tối đa 255 kí tự")
    private String supplierAddress;

    private Boolean isActive;

    public String getSupplierName(){
        return supplierName;
    }
    public void setSupplierName(String supplierName){
        this.supplierName = supplierName;
    }

    public String getSupplierPhone(){
        return supplierPhone;
    }
    public void setSupplierPhone(String supplierPhone){
        this.supplierPhone = supplierPhone;
    }

    public String getSupplierEmail(){
        return supplierEmail;
    }
    public void setSupplierEmail(String supplierEmail){
        this.supplierEmail = supplierEmail;
    }

    public String getSupplierAddress(){
        return supplierAddress;
    }
    public void setSupplierAddress(String supplierAddress){
        this.supplierAddress = supplierAddress;
    }

    public Boolean getIsActive(){
        return isActive;
    }
    public void setIsActive(Boolean IsActive){
        this.isActive = IsActive;
    }
}
