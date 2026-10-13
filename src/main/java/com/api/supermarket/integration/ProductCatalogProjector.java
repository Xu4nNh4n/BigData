package com.api.supermarket.integration;

import com.api.supermarket.entity.Product;
import com.api.supermarket.mongodb.document.ProductCatalogDocument;
import com.api.supermarket.repository.InventoryBatchRepository;
import com.api.supermarket.repository.ProductRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;

@Service
public class ProductCatalogProjector {
    private final ProductRepository products;
    private final InventoryBatchRepository batches;
    private final MongoTemplate mongo;
    private volatile boolean indexReady;

    public ProductCatalogProjector(ProductRepository products, InventoryBatchRepository batches, MongoTemplate mongo) {
        this.products = products;
        this.batches = batches;
        this.mongo = mongo;
    }

    public void sync(Long productId) {
        // The dispatcher uses one worker per process; do not hold a product write lock during cloud I/O.
        Product product = products.findById(productId).orElse(null);
        if (product == null) {
            mongo.remove(Query.query(Criteria.where("productId").is(productId)), ProductCatalogDocument.class);
            return;
        }
        ensureIndex();
        // Matching by either field adopts existing Atlas seed documents and handles a changed SKU.
        Query query = Query.query(new Criteria().orOperator(
                Criteria.where("productId").is(productId), Criteria.where("sku").is(product.getSkuCode())));
        var snapshots = batches.findByProductIdOrderByIdAsc(productId).stream().map(batch -> {
            var snapshot = new ProductCatalogDocument.BatchSnapshot();
            snapshot.setBatchCode(batch.code);
            snapshot.setQuantity(batch.remaining);
            snapshot.setReservedQuantity(batch.reserved);
            snapshot.setDamagedQuantity(batch.damaged);
            snapshot.setStatus(batch.status);
            snapshot.setExpiryDate(batch.expiryDate == null ? null : batch.expiryDate.atStartOfDay().toInstant(ZoneOffset.UTC));
            return snapshot;
        }).toList();
        Update update = new Update()
                .set("productId", productId).set("sku", product.getSkuCode())
                .set("name", product.getProductName()).set("price", product.getPrice())
                .set("active", product.getIsActive()).set("imageUrl", product.getImageUrl())
                .set("categoryId", product.getCategory().getCategoryId())
                .set("categoryName", product.getCategory().getCategoryName())
                .set("supplierId", product.getSupplier().getSupplierId())
                .set("supplierName", product.getSupplier().getSupplierName())
                .set("batches", snapshots).set("syncedAt", Instant.now());
        // attributes belongs to MongoDB and is deliberately preserved by this partial update.
        mongo.upsert(query, update, ProductCatalogDocument.class);
    }

    private synchronized void ensureIndex() {
        if (indexReady) return;
        mongo.indexOps(ProductCatalogDocument.class).createIndex(new Index().on("sku", Sort.Direction.ASC).unique());
        indexReady = true;
    }
}
