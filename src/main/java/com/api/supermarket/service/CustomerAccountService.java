package com.api.supermarket.service;

import com.api.supermarket.dto.request.RegisterCustomerRequest;
import com.api.supermarket.dto.response.CustomerResponse;

public interface CustomerAccountService {
    CustomerResponse register(RegisterCustomerRequest request);
}
