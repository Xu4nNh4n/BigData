package com.api.supermarket.repository;

import com.api.supermarket.entity.*;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.productId = :id")
  Optional<Product> findByIdForUpdate(@Param("id") Long id);

  @EntityGraph(attributePaths = { "category", "supplier" })
  @Override
  List<Product> findAll();

  @EntityGraph(attributePaths = { "category", "supplier" })
  @Override
  Optional<Product> findById(Long id);

  boolean existsByCategory_CategoryId(Long categoryId);

  // Kiểm tra xóa nhà cung cấp có sản phẩm liên quan hay không
  boolean existsBySupplier_SupplierId(Long supplierId);

  // Lọc sản phẩm đang hoạt động
  @EntityGraph(attributePaths = { "category", "supplier" })
  List<Product> findByIsActiveTrue();

  // Kiểm tra trùng mã sku
  boolean existsBySkuCode(String skuCode);

  // Lọc sản phẩm theo danh mục
  @EntityGraph(attributePaths = { "category", "supplier" })
  List<Product> findByCategory_CategoryId(Long categoryId);

  // Lọc sản phẩm theo nhà cung cấp
  @EntityGraph(attributePaths = { "category", "supplier" })
  List<Product> findBySupplier_SupplierId(Long supplierId);

  // Tìm sản phẩm theo mã sku
  @EntityGraph(attributePaths = { "category", "supplier" })
  Product findBySkuCode(String skuCode);

  // Lấy sản phẩm sắp hết hạn
  // Vì cần so sánh stockQuantity <= reorderPoint nên dùng @Query
  @EntityGraph(attributePaths = { "category", "supplier" })
  @Query("SELECT p FROM Product p WHERE p.isActive = true AND "
      + "(SELECT COALESCE(SUM(b.remaining - b.reserved), 0) FROM InventoryBatch b "
      + "WHERE b.productId = p.productId AND b.status = 'ACTIVE' "
      + "AND (b.expiryDate IS NULL OR b.expiryDate >= CURRENT_DATE)) <= p.reorderPoint")
  List<Product> findLowStockProducts();

  // Filter products
  @EntityGraph(attributePaths = { "category", "supplier" })
  @Query("""
      SELECT p FROM Product p
      WHERE (:keyword IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:categoryId IS NULL OR p.category.categoryId = :categoryId)
        AND (:supplierId IS NULL OR p.supplier.supplierId = :supplierId)
        AND (:active IS NULL OR p.isActive = :active)
      """)
  Page<Product> filterProducts(
      @Param("keyword") String keyword,
      @Param("categoryId") Long categoryId,
      @Param("supplierId") Long supplierId,
      @Param("active") Boolean active,
      Pageable pageable);
}
