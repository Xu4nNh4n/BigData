package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.api.supermarket.dto.request.RegisterCustomerRequest;
import com.api.supermarket.dto.response.CustomerResponse;
import com.api.supermarket.entity.Customer;
import com.api.supermarket.entity.Role;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.CustomerRepository;
import com.api.supermarket.repository.RoleRepository;
import com.api.supermarket.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class CustomerAccountServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerAccountServiceImpl customerAccountService;

    private RegisterCustomerRequest createValidRequest() {
        RegisterCustomerRequest req = new RegisterCustomerRequest();
        req.setUsername("khachhang1");
        req.setEmail("khachhang1@gmail.com");
        req.setPhone("0912345678");
        req.setFullName("Lê Thị Khách");
        req.setPassword("Password@123");
        req.setAddress("456 Hai Bà Trưng");
        return req;
    }

    @Test
    void register_Success() {
        RegisterCustomerRequest req = createValidRequest();

        when(userRepository.existsByUserName("khachhang1")).thenReturn(false);
        when(userRepository.existsByEmail("khachhang1@gmail.com")).thenReturn(false);
        when(customerRepository.existsByEmailIgnoreCase("khachhang1@gmail.com")).thenReturn(false);
        when(customerRepository.existsByPhone("0912345678")).thenReturn(false);

        Role customerRole = new Role();
        customerRole.setRoleId(3L);
        customerRole.setRoleName("ROLE_CUSTOMER");
        when(roleRepository.findByRoleNameIgnoreCase("ROLE_CUSTOMER")).thenReturn(Optional.of(customerRole));

        when(passwordEncoder.encode("Password@123")).thenReturn("encodedHash");

        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setUserId(10L);
            return u;
        });

        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setCustomerId(50L);
            return c;
        });

        CustomerResponse response = customerAccountService.register(req);

        assertNotNull(response);
        assertEquals(50L, response.getCustomerId());
        assertEquals("Lê Thị Khách", response.getFullName());
        assertEquals("khachhang1@gmail.com", response.getEmail());
        assertEquals("0912345678", response.getPhone());
        verify(userRepository).save(any(User.class));
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void register_DuplicateUsername_ThrowsBadRequest() {
        RegisterCustomerRequest req = createValidRequest();
        when(userRepository.existsByUserName("khachhang1")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> customerAccountService.register(req));
        assertEquals("Tên đăng nhập, email hoặc số điện thoại đã được sử dụng", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_DuplicatePhone_ThrowsBadRequest() {
        RegisterCustomerRequest req = createValidRequest();
        when(userRepository.existsByUserName("khachhang1")).thenReturn(false);
        when(userRepository.existsByEmail("khachhang1@gmail.com")).thenReturn(false);
        when(customerRepository.existsByEmailIgnoreCase("khachhang1@gmail.com")).thenReturn(false);
        when(customerRepository.existsByPhone("0912345678")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> customerAccountService.register(req));
        assertEquals("Tên đăng nhập, email hoặc số điện thoại đã được sử dụng", ex.getMessage());
    }

    @Test
    void register_PasswordTooLong_ThrowsBadRequest() {
        RegisterCustomerRequest req = createValidRequest();
        req.setPassword("a".repeat(73)); // > 72 bytes

        when(userRepository.existsByUserName("khachhang1")).thenReturn(false);
        when(userRepository.existsByEmail("khachhang1@gmail.com")).thenReturn(false);
        when(customerRepository.existsByEmailIgnoreCase("khachhang1@gmail.com")).thenReturn(false);
        when(customerRepository.existsByPhone("0912345678")).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> customerAccountService.register(req));
        assertEquals("Mật khẩu vượt giới hạn 72 byte của BCrypt", ex.getMessage());
    }

    @Test
    void getProfile_Success() {
        User user = new User();
        user.setUserId(10L);
        user.setUserName("khachhang1");

        Customer customer = new Customer();
        customer.setCustomerId(50L);
        customer.setUser(user);
        customer.setFullName("Lê Thị Khách");
        customer.setEmail("khachhang1@gmail.com");
        customer.setPhone("0912345678");

        when(userRepository.findByUserName("khachhang1")).thenReturn(Optional.of(user));
        when(customerRepository.findByUser_UserId(10L)).thenReturn(Optional.of(customer));

        CustomerResponse response = customerAccountService.getProfile("khachhang1");

        assertNotNull(response);
        assertEquals(50L, response.getCustomerId());
        assertEquals("Lê Thị Khách", response.getFullName());
    }

    @Test
    void getProfile_UserNotFound_ThrowsResourceNotFound() {
        when(userRepository.findByUserName("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerAccountService.getProfile("unknown"));
    }
}
