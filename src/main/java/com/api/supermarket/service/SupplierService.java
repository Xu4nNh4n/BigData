package com.api.supermarket.service;
import com.api.supermarket.dto.request.SupplierRequest;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.dto.response.SupplierResponse;
public interface SupplierService {

    //Lấy 1 nhà cung cấp bằng id
    SupplierResponse getSupplierbyId(Long id);

    //Thêm nhà cung cấp mới
    SupplierResponse createSupplier(SupplierRequest request);

    //Sửa nhà cung cấp
    SupplierResponse updateSupplier(Long id, SupplierRequest request);

    //Xóa nhà cung cấp
    void deleteSupplier(Long id);

    PageResponse<SupplierResponse> filterSuppliers(
        int page,
        int size,
        String sortBy,
        String sortDir, // Sắp xếp theo trường nào
        String keyword, // Từ khóa tìm kiếm theo tên nhà cung cấp
        String phone,
        String email,
        String address,
        Boolean active // Lọc theo trạng thái hoạt động
    );


} 


