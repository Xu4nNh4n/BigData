package com.api.supermarket.service.Impl;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.api.supermarket.dto.request.SupplierRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.SupplierResponse;
import com.api.supermarket.entity.Supplier;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.ProductRepository;
import com.api.supermarket.repository.SupplierRepository;
import com.api.supermarket.service.SupplierService;
import com.api.supermarket.validation.PaginationValidator;
import com.api.supermarket.integration.DataSyncPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierServiceImpl implements SupplierService {
    private final ProductRepository productRepository;
    // Repository là lớp trung gian làm việc với database.
    // Service cần nó để lấy, thêm, sửa, xóa dữ liệu nhà cung cấp.
    private final SupplierRepository supplierRepository;
    private final DataSyncPublisher dataSync;

    private SupplierResponse mapToResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getSupplierId(),
                supplier.getSupplierName(),
                supplier.getSupplierPhone(),
                supplier.getSupplierEmail(),
                supplier.getSupplierAddress(),
                supplier.getIsActive(),
                supplier.getCreateAt());
    }

    // Constructor dùng để Spring tự truyền SupplierRepository vào khi tạo
    // SupplierServiceImpl.
    // Cần constructor này để service không phải tự tạo repository thủ công.
    public SupplierServiceImpl(SupplierRepository supplierRepository, ProductRepository productRepository, DataSyncPublisher dataSync) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.dataSync = dataSync;
    }

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "supplierId",
            "supplierName",
            "supplierPhone",
            "supplierEmail",
            "supplierAddress",
            "isActive",
            "createAt");

    @Override
    public SupplierResponse getSupplierbyId(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp này"));
        return mapToResponse(supplier);
    }

    @Override
    public SupplierResponse createSupplier(SupplierRequest request) {
        // isBlank kiểm tra chuỗi có rỗng hay không dù có kí tự trống
        if (request.getSupplierEmail() != null && !request.getSupplierEmail().isBlank()) {
            if (supplierRepository.existsBySupplierEmail(request.getSupplierEmail())) {
                throw new BadRequestException("Email nhà cung cấp đã tồn tại");
            }
        }

        if (request.getSupplierPhone() != null && !request.getSupplierPhone().isBlank()) {
            if (supplierRepository.existsBySupplierPhone(request.getSupplierPhone())) {
                throw new BadRequestException("Số điện thoại nhà cung cấp đã tồn tại");
            }
        }
        Supplier supplier = new Supplier();
        supplier.setSupplierName(request.getSupplierName());
        supplier.setSupplierPhone(request.getSupplierPhone());
        supplier.setSupplierEmail(request.getSupplierEmail());
        supplier.setSupplierAddress(request.getSupplierAddress());
        supplier.setIsActive(request.getIsActive() == null ? true : request.getIsActive());

        return mapToResponse(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp này"));
        if (request.getSupplierEmail() != null && !request.getSupplierEmail().isBlank()) {
            if (!request.getSupplierEmail().equals(supplier.getSupplierEmail())
                    && supplierRepository.existsBySupplierEmail(request.getSupplierEmail())) {
                throw new BadRequestException("Email nhà cung cấp đã tồn tại");
            }
        }
        if (request.getSupplierPhone() != null && !request.getSupplierPhone().isBlank()) {
            if (!request.getSupplierPhone().equals(supplier.getSupplierPhone())
                    && supplierRepository.existsBySupplierPhone(request.getSupplierPhone())) {
                throw new BadRequestException("Số điện thoại nhà cung cấp đã tồn tại");
            }
        }

        supplier.setSupplierName(request.getSupplierName());
        supplier.setSupplierPhone(request.getSupplierPhone());
        supplier.setSupplierEmail(request.getSupplierEmail());
        supplier.setSupplierAddress(request.getSupplierAddress());
        supplier.setIsActive(request.getIsActive() != null ? request.getIsActive() : supplier.getIsActive());

        Supplier saved = supplierRepository.save(supplier);
        productRepository.findBySupplier_SupplierId(id).forEach(product -> dataSync.catalog(product.getProductId()));
        return mapToResponse(saved);
    }

    @Override
    public void deleteSupplier(Long id) {

        if (productRepository.existsBySupplier_SupplierId(id)) {
            throw new BadRequestException("Không thể xóa nhà cung cấp này vì có sản phẩm liên quan");
        }

        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cập này"));
        supplierRepository.delete(supplier);
    }

    @Override
    public PageResponse<SupplierResponse> filterSuppliers(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String keyword,
            String phone,
            String email,
            String address,
            Boolean active) {
        PaginationValidator.validate(page, size, sortBy, sortDir, ALLOWED_SORT_FIELDS);

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Supplier> supplierPage = supplierRepository.filterSuppliers(
                keyword,
                phone,
                email,
                address,
                active,
                pageable);

        List<SupplierResponse> suppliers = supplierPage.getContent()
                .stream()
                .map(this::mapToResponse)
                .toList();

        return new PageResponse<>(
                suppliers,
                supplierPage.getNumber(), // Số trang hiện tại
                supplierPage.getSize(), // Số lượng nhà cung cấp trong trang hiện tại
                supplierPage.getTotalElements(), // Tổng số nhà cung cấp
                supplierPage.getTotalPages(), // Tổng số trang
                supplierPage.isLast() // Kiểm tra xem có phải trang cuối cùng không
        );
    }

}
