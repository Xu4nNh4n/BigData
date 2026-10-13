package com.api.supermarket.repository;

import com.api.supermarket.entity.Category;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long>{

    //Kiểm tra tên danh mục đã tồn tại hay chưa
    boolean existsByCategoryName(String categoryName);

    @Query("""
            SELECT c FROM Category c
            WHERE (:keyword IS NULL OR LOWER(c.categoryName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:description IS NULL OR LOWER(c.description) LIKE LOWER(CONCAT('%', :description, '%')))
            AND (:active IS NULL OR c.isActive = :active)
            """)
        Page<Category> filterCategories(
            @Param("keyword") String keyword,
            @Param("description") String description,
            @Param("active") Boolean active,
            Pageable pageable
        );
}
