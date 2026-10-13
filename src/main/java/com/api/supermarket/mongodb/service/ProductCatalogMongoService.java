package com.api.supermarket.mongodb.service;

import com.api.supermarket.mongodb.document.ProductCatalogDocument;
import com.api.supermarket.mongodb.repository.ProductCatalogMongoRepository;
import org.springframework.stereotype.Service;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.exception.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import java.util.Map;

@Service
public class ProductCatalogMongoService {

    private final ProductCatalogMongoRepository repository;
    private final MongoTemplate mongo;

    public ProductCatalogMongoService(ProductCatalogMongoRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    public PageResponse<ProductCatalogDocument> findActiveProducts(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.api.supermarket.exception.BadRequestException("page >= 0, size từ 1 đến 100");
        }
        var result = repository.findByActiveTrue(PageRequest.of(page, size, Sort.by("sku")));
        return new PageResponse<>(result.getContent(), page, size, result.getTotalElements(),
                result.getTotalPages(), result.isLast());
    }

    public ProductCatalogDocument findBySku(String sku) {
        return repository.findBySku(sku).filter(ProductCatalogDocument::isActive).orElse(null);
    }

    public ProductCatalogDocument updateAttributes(String sku, Map<String, Object> attributes) {
        if (repository.findBySku(sku).isEmpty()) {
            throw new ResourceNotFoundException("Sản phẩm chưa được đồng bộ vào catalog");
        }
        mongo.updateFirst(Query.query(Criteria.where("sku").is(sku)),
                new Update().set("attributes", attributes), ProductCatalogDocument.class);
        return repository.findBySku(sku).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy catalog"));
    }
}
