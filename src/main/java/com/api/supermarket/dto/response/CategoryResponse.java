package com.api.supermarket.dto.response;

import java.time.LocalDateTime;

public class CategoryResponse {
    private Long categoryId;
    private String categoryName;
    private String description;
    private Boolean isActive;
    private LocalDateTime createAt;

    public CategoryResponse(Long categoryId, String categoryName, String description, Boolean isActive, LocalDateTime createAt) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.description = description;
        this.isActive = isActive;
        this.createAt = createAt;
    }

    //getters and setters

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
