package com.api.supermarket.service.Impl;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.supermarket.dto.request.ProductRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.ProductResponse;
import com.api.supermarket.entity.Category;
import com.api.supermarket.entity.Product;
import com.api.supermarket.entity.Supplier;
import com.api.supermarket.entity.StockMovement;
import com.api.supermarket.repository.StockMovementRepository;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.CategoryRepository;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.SupplierRepository;
import com.api.supermarket.service.ProductService;
import com.api.supermarket.validation.PaginationValidator;
import com.api.supermarket.integration.DataSyncPublisher;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {
    // Repository dùng để thao tác trực tiếp với bảng product trong database.
    // Service cần repository để tách phần xử lý nghiệp vụ khỏi phần truy vấn dữ
    // liệu
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;
    private final com.api.supermarket.service.InventoryBatchService inventoryBatchService;
    private final DataSyncPublisher dataSync;

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    // Constructor injection: Spring sẽ tự truyền ProductRepository vào đây.
    // Cách này giúp service dùng được repository mà không cần tự new object.
    public ProductServiceImpl(ProductRepository productRepository, CategoryRepository categoryRepository,
            SupplierRepository supplierRepository, StockMovementRepository stockMovementRepository,
            com.api.supermarket.service.InventoryBatchService inventoryBatchService, DataSyncPublisher dataSync) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.inventoryBatchService = inventoryBatchService;
        this.dataSync = dataSync;
    }

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "productId",
            "productName",
            "skuCode",
            "categoryId",
            "supplierId",
            "price",
            "stockQuantity",
            "isActive",
            "createAt");

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = new ProductResponse(
                product.getProductId(),
                product.getProductName(),
                product.getSkuCode(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getReorderPoint(),
                product.getExpiryDate(),
                product.getImageUrl(),
                product.getIsActive(),
                product.getCategory() != null ? product.getCategory().getCategoryId() : null,
                product.getCategory() != null ? product.getCategory().getCategoryName() : null,
                product.getSupplier() != null ? product.getSupplier().getSupplierId() : null,
                product.getSupplier() != null ? product.getSupplier().getSupplierName() : null,
                product.getCreateAt());
        response.setReservedQuantity(product.getReservedQuantity());
        response.setDamagedQuantity(product.getDamagedQuantity());
        response.setAvailableQuantity(product.getIsActive() ? inventoryBatchService.usableQuantity(product.getProductId()) : 0);
        return response;
    }

    // Các phương thức còn lại của ProductServiceImpl sẽ được triển khai ở đây, bao
    // gồm các phương thức CRUD và các phương thức tìm kiếm/lọc sản phẩm.
    // Get sản phẩm theo id
    @Override
    public ProductResponse getProductById(Long id) {
        log.debug("Loading product, productId={}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));

        log.debug("Product found, productId={}", id);

        return mapToResponse(product);
    }

    // Thêm sản phẩm mới
    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        // Kiểm tra xem mã SKU đã tồn tại chưa
        if (productRepository.existsBySkuCode(request.getSkuCode())) {
            throw new BadRequestException("Mã SKU đã tồn tại");
        }
        log.debug("Creating product, productName={}, productSku={}", request.getProductName(), request.getSkuCode());
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại"));
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Nhà cung cấp không tồn tại"));
        // Tạo entity mới vì database lưu Product, không lưu trực tiếp ProductRequest.
        Product product = new Product();
        product.setSkuCode(request.getSkuCode());
        product.setProductName(request.getProductName());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        if (request.getReorderPoint() != null) {
            product.setReorderPoint(request.getReorderPoint());
        }
        product.setExpiryDate(request.getExpiryDate());
        product.setImageUrl(request.getImageUrl());
        product.setIsActive(request.getIsActive() == null || request.getIsActive());
        product.setCategory(category);
        product.setSupplier(supplier);
        Product saveProduct = productRepository.save(product);
        inventoryBatchService.opening(saveProduct);
        if (saveProduct.getStockQuantity() > 0) {
            recordStockChange(saveProduct, saveProduct.getStockQuantity(), "OPENING", "Tồn kho khởi tạo");
        }
        log.info("Product created successfully, productId={}", saveProduct.getProductId());
        dataSync.catalog(saveProduct.getProductId());
        return mapToResponse(saveProduct);
    }
    // Cập nhật sản phẩm

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));

        // Kiểm tra xem mã SKU đã tồn tại chưa (ngoại trừ sản phẩm hiện tại)
        if (!product.getSkuCode().equals(request.getSkuCode())
                && productRepository.existsBySkuCode(request.getSkuCode())) {
            throw new BadRequestException("Mã SKU đã tồn tại");
        }
        log.debug("Updating product, productId={}, productName={}, productSku={}", id, request.getProductName(),
                request.getSkuCode());

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại"));
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Nhà cung cấp không tồn tại"));

        // Cập nhật thông tin sản phẩm
        product.setSkuCode(request.getSkuCode());
        product.setProductName(request.getProductName());
        product.setPrice(request.getPrice());
        if (!product.getStockQuantity().equals(request.getStockQuantity())) {
            throw new BadRequestException("Nhập hoặc điều chỉnh tồn kho qua lô hàng để lưu lịch sử và tránh ghi đè tồn kho");
        }
        if (request.getReorderPoint() != null) {
            product.setReorderPoint(request.getReorderPoint());
        }
        product.setExpiryDate(request.getExpiryDate());
        product.setImageUrl(request.getImageUrl());
        product.setIsActive(request.getIsActive() != null ? request.getIsActive() : product.getIsActive());
        product.setCategory(category);
        product.setSupplier(supplier);

        Product updatedProduct = productRepository.save(product);
        dataSync.catalog(updatedProduct.getProductId());
        log.info("Product updated successfully, productId={}", updatedProduct.getProductId());
        return mapToResponse(updatedProduct);
    }

    // Xóa sản phẩm
    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm"));
        log.debug("Deleting product, productId={}, productName={}, productSku={}", id, product.getProductName(),
                product.getSkuCode());
        product.setIsActive(false);
        productRepository.save(product);
        dataSync.catalog(product.getProductId());
        log.info("Product deleted successfully, productId={}", id);
    }

    private void recordStockChange(Product product, int quantity, String type, String note) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setQuantity(quantity);
        movement.setMovementType(type);
        movement.setNote(note);
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        movement.setPerformedBy(authentication == null ? "SYSTEM" : authentication.getName());
        dataSync.movement(stockMovementRepository.save(movement));
    }

    @Override
    public PageResponse<com.api.supermarket.dto.response.StockMovementResponse> getStockMovements(Long productId, int page, int size) {
        PaginationValidator.validate(page, size, "movementId", "desc", Set.of("movementId"));
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm");
        }
        var movements = stockMovementRepository.findByProduct_ProductIdOrderByMovementIdDesc(productId, PageRequest.of(page, size));
        var content = movements.getContent().stream().map(m -> new com.api.supermarket.dto.response.StockMovementResponse(
                m.getMovementId(), productId, m.getOrderId(), m.getMovementType(), m.getQuantity(), m.getNote(),
                m.getPerformedBy(), m.getCreatedAt())).toList();
        return new PageResponse<>(content, movements.getNumber(), movements.getSize(), movements.getTotalElements(),
                movements.getTotalPages(), movements.isLast());
    }

    // Lấy danh sách sản phẩm đang hoạt động
    @Override
    public List<ProductResponse> getActiveProducts() {
        return productRepository.findByIsActiveTrue().stream().map(this::mapToResponse).toList();
    }

    // Tìm kiếm sản phẩm theo mã SKU
    @Override
    public ProductResponse getProductBySkuCode(String skuCode) {
        Product product = productRepository.findBySkuCode(skuCode);
        if (product == null) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm");
        }
        return mapToResponse(product);
    }

    // Lấy sản phẩm sắp hết hạn (stockQuantity <= reorderPoint)
    @Override
    public List<ProductResponse> getLowStockProducts() {
        return productRepository.findLowStockProducts().stream().map(this::mapToResponse).toList();
    }

    @Override
    public PageResponse<ProductResponse> filterProducts(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String keyword,
            Long categoryId,
            Long supplierId,
            Boolean active) {

        PaginationValidator.validate(page, size, sortBy, sortDir, ALLOWED_SORT_FIELDS);

        String sortProperty = switch (sortBy) {
            case "categoryId" -> "category.categoryId";
            case "supplierId" -> "supplier.supplierId";
            default -> sortBy;
        };

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortProperty).descending()
                : Sort.by(sortProperty).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> productPage = productRepository.filterProducts(
                keyword,
                categoryId,
                supplierId,
                active,
                pageable);
        List<ProductResponse> products = productPage.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return new PageResponse<>(
                products,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.isLast());
    }
}
