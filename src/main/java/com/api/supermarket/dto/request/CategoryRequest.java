package com.api.supermarket.dto.request;

import jakarta.validation.constraints.*;

public class CategoryRequest {
    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
    private String categoryName;

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    private String description;

    private Boolean isActive;


    public String getCategoryName(){
        return categoryName;
    }
    public void setCategoryName(String categoryName){
        this.categoryName = categoryName;
    }

    public String getDescription(){
        return description;
    }
    public void setDescription(String description){
        this.description = description;
    }

    public Boolean getIsActive(){
        return isActive;
    }
    public void setIsActive(Boolean isActive){
        this.isActive = isActive;
    }

}
