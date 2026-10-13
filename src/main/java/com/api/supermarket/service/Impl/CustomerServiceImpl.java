package com.api.supermarket.service.Impl;

import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.supermarket.dto.request.CustomerRequest;
import com.api.supermarket.dto.response.CustomerResponse;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.entity.Customer;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.CustomerRepository;
import com.api.supermarket.service.CustomerService;
import com.api.supermarket.validation.PaginationValidator;

/**
 * 👥 CustomerServiceImpl - QUẢN LÝ THÔNG TIN KHÁCH HÀNG
 * 
 * 💡 ĐẶC ĐIỂM NGHIỆP VỤ:
 * 1. Chống trùng SĐT & Email: Kiểm tra bằng existsByPhone, existsByEmail trước khi lưu.
 * 2. Cập nhật an toàn với Objects.equals(): Đổi SĐT/Email sang giá trị mới thì mới check trùng.
 * 3. Hỗ trợ tra cứu nhanh tại quầy: Tìm kiếm chính xác theo số điện thoại.
 */
@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);

    // Whitelist danh sách các trường được phép sắp xếp
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "customerId", "fullName", "phone", "email", "isActive");

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * 🛠️ Helper Mapper: Chuyển Customer Entity sang CustomerResponse DTO
     */
    private CustomerResponse mapToResponse(Customer customer) {
        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAddress(), customer.getIsActive(),
                customer.getCreateAt());
    }

    /**
     * ➕ Tạo khách hàng thành viên mới
     */
    @Override
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        log.info("Bắt đầu tạo khách hàng mới: {}", request.getFullName());

        // Kiểm tra trùng SĐT nếu có nhập
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            if (customerRepository.existsByPhone(request.getPhone())) {
                throw new BadRequestException("Số điện thoại '" + request.getPhone() + "' đã được sử dụng bởi khách hàng khác!");
            }
        }

        // Kiểm tra trùng Email nếu có nhập
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            if (customerRepository.existsByEmailIgnoreCase(request.getEmail())) {
                throw new BadRequestException("Email '" + request.getEmail() + "' đã được sử dụng bởi khách hàng khác!");
            }
        }

        Customer customer = new Customer();
        customer.setFullName(request.getFullName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        customer.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        Customer savedCustomer = customerRepository.save(customer);
        log.info("Tạo khách hàng thành công, ID = {}", savedCustomer.getCustomerId());
        return mapToResponse(savedCustomer);
    }

    /**
     * ✏️ Cập nhật thông tin khách hàng
     */
    @Override
    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        log.info("Cập nhật thông tin khách hàng ID = {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với ID: " + id));

        // Kiểm tra trùng SĐT mới (chỉ kiểm tra nếu khách đổi sang một số điện thoại khác)
        if (request.getPhone() != null && !Objects.equals(customer.getPhone(), request.getPhone())) {
            if (customerRepository.existsByPhone(request.getPhone())) {
                throw new BadRequestException("Số điện thoại '" + request.getPhone() + "' đã được sử dụng!");
            }
        }

        // Kiểm tra trùng Email mới (chỉ kiểm tra nếu khách đổi sang một email khác)
        if (request.getEmail() != null && !Objects.equals(customer.getEmail(), request.getEmail())) {
            if (customerRepository.existsByEmailIgnoreCase(request.getEmail())) {
                throw new BadRequestException("Email '" + request.getEmail() + "' đã được sử dụng!");
            }
        }

        customer.setFullName(request.getFullName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        if (request.getIsActive() != null) {
            customer.setIsActive(request.getIsActive());
        }

        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Cập nhật khách hàng thành công, ID = {}", id);
        return mapToResponse(updatedCustomer);
    }

    /**
     * 🗑️ Xóa khách hàng (Chỉ Admin)
     */
    @Override
    @Transactional
    public void deleteCustomer(Long id) {
        log.info("Xóa khách hàng ID = {}", id);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với ID: " + id));
        customer.setIsActive(false);
        customerRepository.save(customer);
    }

    /**
     * 🔎 Tìm kiếm & lọc phân trang khách hàng
     */
    @Override
    public PageResponse<CustomerResponse> filterCustomers(
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

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Customer> customerPage = customerRepository.filterCustomers(
                keyword, phone, email, address, active, pageable);

        return new PageResponse<>(
                customerPage.getContent().stream().map(this::mapToResponse).toList(),
                customerPage.getNumber(),
                customerPage.getSize(),
                customerPage.getTotalElements(),
                customerPage.getTotalPages(),
                customerPage.isLast());
    }
}
