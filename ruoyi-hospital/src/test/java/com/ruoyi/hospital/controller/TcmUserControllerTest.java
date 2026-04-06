package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class TcmUserControllerTest
{
    @Mock
    private ISysUserService userService;

    @Mock
    private ISysRoleService roleService;

    @Mock
    private ITcmPatientService patientService;

    private TcmUserController controller;

    @BeforeEach
    void setUp()
    {
        controller = new TcmUserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "roleService", roleService);
        ReflectionTestUtils.setField(controller, "patientService", patientService);

        SysUser loginUserEntity = new SysUser();
        loginUserEntity.setUserId(1L);
        SysRole adminRole = new SysRole();
        adminRole.setRoleId(1L);
        adminRole.setRoleKey("admin");
        adminRole.setFlag(true);
        loginUserEntity.setRoles(List.of(adminRole));

        LoginUser loginUser = new LoginUser(loginUserEntity, Collections.emptySet());
        loginUser.setUserId(1L);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void update_shouldRetainExistingRoleIdsWhenPayloadOmitsRoles()
    {
        SysRole practitioner = buildPractitionerRole();
        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setRoles(List.of(practitioner));

        when(userService.selectUserById(42L)).thenReturn(stored);
        when(userService.updateUserProfile(any())).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("phone", "13800000003");

        controller.update(42L, body);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUserProfile(captor.capture());
        verify(userService, never()).updateUser(any());
        assertEquals("13800000003", captor.getValue().getPhonenumber());
    }

    @Test
    void update_shouldSyncUserNameWhenEmailChanges()
    {
        SysRole practitioner = buildPractitionerRole();
        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setRoles(List.of(practitioner));

        when(userService.selectUserById(42L)).thenReturn(stored);
        when(userService.selectUserByUserName("new-doctor@example.com")).thenReturn(null);
        when(userService.updateUserProfile(any())).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("email", "new-doctor@example.com");

        controller.update(42L, body);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUserProfile(captor.capture());
        assertEquals("new-doctor@example.com", captor.getValue().getUserName());
    }

    @Test
    void update_shouldUseFullUpdateWhenRolesProvided()
    {
        SysRole practitioner = buildPractitionerRole();
        SysRole cashier = new SysRole();
        cashier.setRoleId(3L);
        cashier.setRoleKey("cashier");
        cashier.setFlag(true);

        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setRoles(List.of(practitioner));

        when(userService.selectUserById(42L)).thenReturn(stored);
        when(roleService.selectRoleAll()).thenReturn(List.of(practitioner, cashier));
        when(userService.updateUser(any())).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("roles", List.of("cashier"));

        controller.update(42L, body);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUser(captor.capture());
        verify(userService, never()).updateUserProfile(any());
        assertArrayEquals(new Long[] { 3L }, captor.getValue().getRoleIds());
    }

    private SysRole buildPractitionerRole()
    {
        SysRole practitioner = new SysRole();
        practitioner.setRoleId(2L);
        practitioner.setRoleKey("practitioner");
        practitioner.setFlag(true);
        return practitioner;
    }
}
