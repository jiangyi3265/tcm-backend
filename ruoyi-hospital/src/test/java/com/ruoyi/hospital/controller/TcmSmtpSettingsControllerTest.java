package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.core.domain.entity.*;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.impl.TcmSmtpSettingsService;

class TcmSmtpSettingsControllerTest
{
    private AnnotationConfigApplicationContext context;
    private TcmSmtpSettingsController controller;
    private TcmSmtpSettingsService smtp;

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class TestConfiguration
    {
        @Bean public PermissionService ss() { return new PermissionService(); }
        @Bean public TcmSmtpSettingsService smtp() { return mock(TcmSmtpSettingsService.class); }
        @Bean public com.ruoyi.hospital.mapper.TcmClinicSettingMapper settingsMapper() { return mock(com.ruoyi.hospital.mapper.TcmClinicSettingMapper.class); }
        @Bean public com.ruoyi.hospital.util.SmtpSecretCipher cipher() { return mock(com.ruoyi.hospital.util.SmtpSecretCipher.class); }
        @Bean public ITcmAuditLogService audit() { return mock(ITcmAuditLogService.class); }
        @Bean public TcmSmtpSettingsController controller() { return new TcmSmtpSettingsController(); }
    }

    @BeforeEach void setup()
    {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        controller = context.getBean(TcmSmtpSettingsController.class);
        smtp = context.getBean(TcmSmtpSettingsService.class);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    private void login(String roleKey)
    {
        SysUser user = new SysUser(); user.setUserId(42L);
        SysRole role = new SysRole(); role.setRoleKey(roleKey); role.setRoleId(2L);
        user.setRoles(List.of(role));
        LoginUser login = new LoginUser(user, Set.of()); login.setUserId(42L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null, List.of()));
    }

    @Test void ordinaryStaffCannotReadChangeOrTestSmtpCredentials()
    {
        login("practitioner");
        assertThrows(AccessDeniedException.class, controller::status);
        assertThrows(AccessDeniedException.class, () -> controller.update(Map.of("password", "private-test-key")));
        assertThrows(AccessDeniedException.class, controller::test);
        verifyNoInteractions(smtp);
    }

    @Test void administratorsCanSaveWithoutPuttingCredentialsIntoTheAuditLog()
    {
        login("admin");
        Map<String, Object> safe = Map.of("passwordConfigured", true);
        when(smtp.status()).thenReturn(safe);
        when(smtp.update(anyMap())).thenReturn(safe);
        when(smtp.testConnection()).thenReturn(Map.of("success", true));
        assertEquals(safe, controller.status());
        assertEquals(safe, controller.update(Map.of("password", "private-test-key")));
        assertEquals(true, controller.test().get("success"));
        verify(context.getBean(ITcmAuditLogService.class)).log("settings", "smtp", "SMTP", "UPDATE", "42", "Updated SMTP configuration");
    }
}
