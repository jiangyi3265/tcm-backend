package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmSettingsService;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.SignedFileUrlService;

@ExtendWith(MockitoExtension.class)
class TcmSettingsControllerTest
{
    @Mock
    private ITcmSettingsService settingsService;

    @Mock
    private ITcmAuditLogService auditLogService;

    @Mock
    private HospitalFileStorage hospitalFileStorage;

    @Mock
    private SignedFileUrlService signedFileUrlService;

    private TcmSettingsController controller;

    @BeforeEach
    void setUp()
    {
        controller = new TcmSettingsController();
        ReflectionTestUtils.setField(controller, "settingsService", settingsService);
        ReflectionTestUtils.setField(controller, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(controller, "hospitalFileStorage", hospitalFileStorage);
        ReflectionTestUtils.setField(controller, "signedFileUrlService", signedFileUrlService);
        setLoginUser(1L, "admin");
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadClinicSeal_shouldPersistClinicSealSetting() throws Exception
    {
        when(hospitalFileStorage.store(any(MultipartFile.class), eq("clinic_seal")))
                .thenReturn("hospital-private/test/clinic-seal.png");
        when(signedFileUrlService.buildAccessUrl("hospital-private/test/clinic-seal.png"))
                .thenReturn("/api/public/files/access?resource=hospital-private/test/clinic-seal.png");

        MockMultipartFile file = new MockMultipartFile("file", "seal.png", "image/png", new byte[] { 1, 2, 3 });

        Map<String, Object> result = controller.uploadClinicSeal(file);

        assertEquals("hospital-private/test/clinic-seal.png", result.get("path"));
        assertEquals("/api/public/files/access?resource=hospital-private/test/clinic-seal.png", result.get("url"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(settingsService).updateBaseSettings(captor.capture());
        Map<String, Object> clinicSeal = (Map<String, Object>) captor.getValue().get("clinicSeal");
        assertEquals("hospital-private/test/clinic-seal.png", clinicSeal.get("path"));
    }

    @Test
    void uploadClinicSeal_shouldNotFailWhenPreviewUrlCannotBeSigned() throws Exception
    {
        when(hospitalFileStorage.store(any(MultipartFile.class), eq("clinic_seal")))
                .thenReturn("hospital-private/test/clinic-seal.png");
        when(signedFileUrlService.buildAccessUrl("hospital-private/test/clinic-seal.png"))
                .thenThrow(new RuntimeException("sign failed"));

        MockMultipartFile file = new MockMultipartFile("file", "seal.png", "image/png", new byte[] { 1, 2, 3 });

        Map<String, Object> result = controller.uploadClinicSeal(file);

        assertEquals("hospital-private/test/clinic-seal.png", result.get("path"));
        assertEquals("", result.get("url"));
        verify(settingsService).updateBaseSettings(any());
    }

    private void setLoginUser(Long userId, String roleKey)
    {
        SysUser loginUserEntity = new SysUser();
        loginUserEntity.setUserId(userId);
        SysRole role = new SysRole();
        role.setRoleId(1L);
        role.setRoleKey(roleKey);
        role.setFlag(true);
        loginUserEntity.setRoles(Collections.singletonList(role));

        LoginUser loginUser = new LoginUser(loginUserEntity, Collections.emptySet());
        loginUser.setUserId(userId);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }
}
