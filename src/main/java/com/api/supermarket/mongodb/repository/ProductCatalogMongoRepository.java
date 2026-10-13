package com.api.supermarket.mongodb.repository;

import com.api.supermarket.mongodb.document.ProductCatalogDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProductCatalogMongoRepository extends MongoRepository<ProductCatalogDocument, String> {
    Optional<ProductCatalogDocument> findBySku(String sku);
    org.springframework.data.domain.Page<ProductCatalogDocument> findByActiveTrue(
            org.springframework.data.domain.Pageable pageable);
}
