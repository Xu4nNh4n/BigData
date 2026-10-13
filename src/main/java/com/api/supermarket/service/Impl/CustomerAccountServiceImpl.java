package com.api.supermarket.service.Impl;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.api.supermarket.dto.request.RegisterCustomerRequest;
import com.api.supermarket.dto.response.CustomerResponse;
import com.api.supermarket.entity.Customer;
import com.api.supermarket.entity.Role;
import com.api.supermarket.entity.User;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.repository.CustomerRepository;
import com.api.supermarket.repository.RoleRepository;
import com.api.supermarket.repository.UserRepository;
import com.api.supermarket.service.CustomerAccountService;

@Service
public class CustomerAccountServiceImpl implements CustomerAccountService {
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerAccountServiceImpl(CustomerRepository customerRepository, UserRepository userRepository,
            RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public CustomerResponse register(RegisterCustomerRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String phone = request.getPhone().trim();
        if (userRepository.existsByUserName(username) || userRepository.existsByEmail(email)
                || customerRepository.existsByEmailIgnoreCase(email) || customerRepository.existsByPhone(phone)) {
            throw new BadRequestException("Tên đăng nhập, email hoặc số điện thoại đã được sử dụng");
        }
        if (request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Mật khẩu vượt giới hạn 72 byte của BCrypt");
        }
        Role role = roleRepository.findByRoleNameIgnoreCase("ROLE_CUSTOMER")
                .or(() -> roleRepository.findByRoleNameIgnoreCase("CUSTOMER"))
                .orElseThrow(() -> new BadRequestException("Chưa cấu hình vai trò CUSTOMER"));
        User user = new User();
        user.setUserName(username);
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoleId(role.getRoleId());
        userRepository.save(user);

        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFullName(user.getFullName());
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setAddress(request.getAddress());
        customer.setCreateAt(LocalDateTime.now());
        return toResponse(customerRepository.save(customer));
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getCustomerId(), customer.getFullName(), customer.getPhone(),
                customer.getEmail(), customer.getAddress(), customer.getIsActive(), customer.getCreateAt());
    }
}
