package com.api.supermarket.mongodb.controller;

import com.api.supermarket.mongodb.document.ProductCatalogDocument;
import com.api.supermarket.mongodb.service.ProductCatalogMongoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.api.supermarket.dto.response.PageResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/catalog/mongodb")
public class ProductCatalogMongoController {

    private final ProductCatalogMongoService service;

    public ProductCatalogMongoController(ProductCatalogMongoService service) {
        this.service = service;
    }

    @GetMapping("/products")
    public PageResponse<ProductCatalogDocument> findProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.findActiveProducts(page, size);
    }

    @GetMapping("/products/{sku}")
    public ResponseEntity<ProductCatalogDocument> findProduct(@PathVariable String sku) {
        ProductCatalogDocument product = service.findBySku(sku);
        return product == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(product);
    }

    @PutMapping("/products/{sku}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProductCatalogDocument upsertProduct(
            @PathVariable String sku,
            @Valid @RequestBody CatalogAttributesRequest request) {
        return service.updateAttributes(sku, request.attributes());
    }

    public record CatalogAttributesRequest(@NotNull @Size(max = 100) Map<String, Object> attributes) {}
}
