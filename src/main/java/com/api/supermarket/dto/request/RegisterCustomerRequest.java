package com.api.supermarket.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterCustomerRequest {
    @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_.-]+")
    private String username;
    @NotBlank @Size(min = 8, max = 72)
    private String password;
    @NotBlank @Size(max = 150)
    private String fullName;
    @NotBlank @Email @Size(max = 100)
    private String email;
    @NotBlank @Size(max = 20)
    private String phone;
    @Size(max = 255)
    private String address;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
