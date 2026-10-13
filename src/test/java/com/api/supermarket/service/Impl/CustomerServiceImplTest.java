package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.api.supermarket.dto.request.CustomerRequest;
import com.api.supermarket.dto.response.CustomerResponse;
import com.api.supermarket.dto.response.PageResponse;
import com.api.supermarket.entity.Customer;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
public class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private CustomerRequest createRequest() {
        CustomerRequest request = new CustomerRequest();
        request.setFullName("Nguyễn Văn A");
        request.setPhone("0987654321");
        request.setEmail("nguyenvana@gmail.com");
        request.setAddress("123 Đường Lê Lợi, TP.HCM");
        request.setPoints(100);
        request.setIsActive(true);
        return request;
    }

    private Customer createCustomerEntity() {
        Customer customer = new Customer();
        customer.setCustomerId(1L);
        customer.setFullName("Nguyễn Văn A");
        customer.setPhone("0987654321");
        customer.setEmail("nguyenvana@gmail.com");
        customer.setAddress("123 Đường Lê Lợi, TP.HCM");
        customer.setPoints(100);
        customer.setIsActive(true);
        customer.setCreateAt(LocalDateTime.now());
        return customer;
    }

    @Test
    void createCustomer_Success() {
        CustomerRequest request = createRequest();
        Customer customer = createCustomerEntity();

        when(customerRepository.existsByPhone(request.getPhone())).thenReturn(false);
        when(customerRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenReturn(customer);

        CustomerResponse response = customerService.createCustomer(request);

        assertNotNull(response);
        assertEquals("Nguyễn Văn A", response.getFullName());
        assertEquals("0987654321", response.getPhone());
        assertEquals(100, response.getPoints());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void createCustomer_DuplicatePhone_ThrowsBadRequestException() {
        CustomerRequest request = createRequest();

        when(customerRepository.existsByPhone(request.getPhone())).thenReturn(true);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            customerService.createCustomer(request);
        });

        assertEquals("Số điện thoại '0987654321' đã được sử dụng bởi khách hàng khác!", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void createCustomer_DuplicateEmail_ThrowsBadRequestException() {
        CustomerRequest request = createRequest();

        when(customerRepository.existsByPhone(request.getPhone())).thenReturn(false);
        when(customerRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(true);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            customerService.createCustomer(request);
        });

        assertEquals("Email 'nguyenvana@gmail.com' đã được sử dụng bởi khách hàng khác!", exception.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void getCustomerById_Success() {
        Customer customer = createCustomerEntity();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomerById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getCustomerId());
        assertEquals("Nguyễn Văn A", response.getFullName());
    }

    @Test
    void getCustomerById_NotFound_ThrowsResourceNotFoundException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            customerService.getCustomerById(99L);
        });

        assertEquals("Không tìm thấy khách hàng với ID: 99", exception.getMessage());
    }

    @Test
    void getCustomerByPhone_Success() {
        Customer customer = createCustomerEntity();
        when(customerRepository.findByPhone("0987654321")).thenReturn(Optional.of(customer));

        CustomerResponse response = customerService.getCustomerByPhone("0987654321");

        assertNotNull(response);
        assertEquals("0987654321", response.getPhone());
    }

    @Test
    void updateCustomer_Success() {
        Customer existingCustomer = createCustomerEntity();
        CustomerRequest updateReq = createRequest();
        updateReq.setFullName("Nguyễn Văn B (Updated)");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(existingCustomer);

        CustomerResponse response = customerService.updateCustomer(1L, updateReq);

        assertNotNull(response);
        verify(customerRepository).save(existingCustomer);
    }

    @Test
    void deleteCustomer_Success_SoftDeletes() {
        Customer customer = createCustomerEntity();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        customerService.deleteCustomer(1L);

        assertFalse(customer.getIsActive());
        verify(customerRepository).save(customer);
    }

    @Test
    void filterCustomers_Success() {
        Customer customer = createCustomerEntity();
        Page<Customer> page = new PageImpl<>(List.of(customer));
        when(customerRepository.filterCustomers(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<CustomerResponse> response = customerService.filterCustomers(
                0, 10, "customerId", "asc", "A", "0987654321", "nguyenvana@gmail.com", "Lê Lợi", true);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Nguyễn Văn A", response.getContent().get(0).getFullName());
    }
}
