package com.api.supermarket.service.Impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.api.supermarket.dto.request.RoleRequest;
import com.api.supermarket.dto.response.RoleResponse;
import com.api.supermarket.entity.Role;
import com.api.supermarket.exception.BadRequestException;
import com.api.supermarket.exception.ResourceNotFoundException;
import com.api.supermarket.repository.RoleRepository;
import com.api.supermarket.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoleServiceImpl roleService;

    private Role createRole(Long id, String name, String desc) {
        Role role = new Role();
        role.setRoleId(id);
        role.setRoleName(name);
        role.setDescription(desc);
        role.setCreateAt(LocalDateTime.now());
        return role;
    }

    @Test
    void getAllRoles_Success() {
        Role role1 = createRole(1L, "ROLE_ADMIN", "Administrator");
        Role role2 = createRole(2L, "ROLE_STAFF", "Staff");
        when(roleRepository.findAll()).thenReturn(List.of(role1, role2));

        List<RoleResponse> result = roleService.getALlRoles();

        assertEquals(2, result.size());
        assertEquals("ROLE_ADMIN", result.get(0).getRoleName());
    }

    @Test
    void getRoleById_Success() {
        Role role = createRole(1L, "ROLE_ADMIN", "Administrator");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        RoleResponse response = roleService.getRoleById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getRoleId());
        assertEquals("ROLE_ADMIN", response.getRoleName());
    }

    @Test
    void getRoleById_NotFound_ThrowsException() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> roleService.getRoleById(99L));
    }

    @Test
    void createRole_Success() {
        RoleRequest req = new RoleRequest();
        req.setRoleName("ROLE_MANAGER");
        req.setDescription("Store Manager");

        when(roleRepository.existsByRoleName("ROLE_MANAGER")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(i -> {
            Role r = i.getArgument(0);
            r.setRoleId(5L);
            return r;
        });

        RoleResponse response = roleService.createRole(req);

        assertNotNull(response);
        assertEquals(5L, response.getRoleId());
        assertEquals("ROLE_MANAGER", response.getRoleName());
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void createRole_DuplicateName_ThrowsBadRequest() {
        RoleRequest req = new RoleRequest();
        req.setRoleName("ROLE_ADMIN");

        when(roleRepository.existsByRoleName("ROLE_ADMIN")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> roleService.createRole(req));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void updateRole_Success() {
        Role existing = createRole(1L, "ROLE_ADMIN", "Old Desc");
        RoleRequest req = new RoleRequest();
        req.setRoleName("ROLE_ADMIN");
        req.setDescription("Updated Desc");

        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roleRepository.save(existing)).thenReturn(existing);

        RoleResponse response = roleService.updateRole(1L, req);

        assertNotNull(response);
        assertEquals("Updated Desc", response.getDescription());
    }

    @Test
    void deleteRole_InUse_ThrowsBadRequest() {
        when(userRepository.existsByRoleId(1L)).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> roleService.deleteRole(1L));
        assertEquals("Vai trò này đang được sử dụng bởi người dùng nên không thể xóa", ex.getMessage());
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void deleteRole_NotInUse_Success() {
        Role role = createRole(10L, "ROLE_TEMP", "Temporary");
        when(userRepository.existsByRoleId(10L)).thenReturn(false);
        when(roleRepository.findById(10L)).thenReturn(Optional.of(role));

        roleService.deleteRole(10L);

        verify(roleRepository).delete(role);
    }

    @Test
    void searchRolesByName_Success() {
        Role role = createRole(1L, "ROLE_ADMIN", "Administrator");
        when(roleRepository.findByRoleNameContaining("ADMIN")).thenReturn(List.of(role));

        List<RoleResponse> result = roleService.searchRolesByName("ADMIN");

        assertEquals(1, result.size());
        assertEquals("ROLE_ADMIN", result.get(0).getRoleName());
    }
}
