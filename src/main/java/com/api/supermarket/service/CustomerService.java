package com.api.supermarket.service;

import com.api.supermarket.dto.request.CustomerRequest;
import com.api.supermarket.dto.response.CustomerResponse;
import com.api.supermarket.dto.response.PageResponse;

public interface CustomerService {

    CustomerResponse createCustomer(CustomerRequest request);

    CustomerResponse updateCustomer(Long id, CustomerRequest request);

    void deleteCustomer(Long id);

    PageResponse<CustomerResponse> filterCustomers(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String keyword,
            String phone,
            String email,
            String address,
            Boolean active);
}
