package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.SignedFileUrlService;
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

    @Mock
    private HospitalFileStorage hospitalFileStorage;

    @Mock
    private SignedFileUrlService signedFileUrlService;

    private TcmUserController controller;

    @BeforeEach
    void setUp()
    {
        controller = new TcmUserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "roleService", roleService);
        ReflectionTestUtils.setField(controller, "patientService", patientService);
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
    void update_shouldRetainExistingRoleIdsWhenPayloadOmitsRoles()
    {
        SysRole practitioner = buildPractitionerRole();
        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setRoles(Collections.singletonList(practitioner));

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
        stored.setRoles(Collections.singletonList(practitioner));

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
        stored.setRoles(Collections.singletonList(practitioner));

        when(userService.selectUserById(42L)).thenReturn(stored);
        when(roleService.selectRoleAll()).thenReturn(Arrays.asList(practitioner, cashier));
        when(userService.updateUser(any())).thenReturn(1);

        Map<String, Object> body = new HashMap<>();
        body.put("roles", Collections.singletonList("cashier"));

        controller.update(42L, body);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUser(captor.capture());
        verify(userService, never()).updateUserProfile(any());
        assertArrayEquals(new Long[] { 3L }, captor.getValue().getRoleIds());
    }

    @Test
    void update_shouldAcceptTenMinuteWorkingHours()
    {
        SysRole practitioner = buildPractitionerRole();
        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setRoles(Collections.singletonList(practitioner));

        when(userService.selectUserById(42L)).thenReturn(stored);
        when(userService.updateUserProfile(any())).thenReturn(1);

        Map<String, Object> mondayRange = new HashMap<>();
        mondayRange.put("start", "14:20");
        mondayRange.put("end", "15:00");
        Map<String, Object> workingHours = new HashMap<>();
        workingHours.put("monday", Collections.singletonList(mondayRange));

        Map<String, Object> body = new HashMap<>();
        body.put("workingHours", workingHours);

        controller.update(42L, body);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUserProfile(captor.capture());
        assertEquals(true, captor.getValue().getRemark().contains("14:20"));
        assertEquals(true, captor.getValue().getRemark().contains("15:00"));
        verify(userService, never()).updateUser(any());
    }

    @Test
    void update_shouldRejectNonTenMinuteWorkingHours()
    {
        SysRole practitioner = buildPractitionerRole();
        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setRoles(Collections.singletonList(practitioner));

        when(userService.selectUserById(42L)).thenReturn(stored);

        Map<String, Object> mondayRange = new HashMap<>();
        mondayRange.put("start", "09:05");
        mondayRange.put("end", "10:00");
        Map<String, Object> workingHours = new HashMap<>();
        workingHours.put("monday", Collections.singletonList(mondayRange));

        Map<String, Object> body = new HashMap<>();
        body.put("workingHours", workingHours);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> controller.update(42L, body));

        assertEquals("工作时间必须按10分钟粒度设置: 09:05", ex.getMessage());
        verify(userService, never()).updateUserProfile(any());
        verify(userService, never()).updateUser(any());
    }

    @Test
    void update_shouldRejectReversedWorkingHourRange()
    {
        SysRole practitioner = buildPractitionerRole();
        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setRoles(Collections.singletonList(practitioner));

        when(userService.selectUserById(42L)).thenReturn(stored);

        Map<String, Object> mondayRange = new HashMap<>();
        mondayRange.put("start", "10:00");
        mondayRange.put("end", "09:30");
        Map<String, Object> workingHours = new HashMap<>();
        workingHours.put("monday", Collections.singletonList(mondayRange));

        Map<String, Object> body = new HashMap<>();
        body.put("workingHours", workingHours);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> controller.update(42L, body));

        assertEquals("工作时间开始时间必须早于结束时间: monday", ex.getMessage());
        verify(userService, never()).updateUserProfile(any());
        verify(userService, never()).updateUser(any());
    }

    @Test
    void update_shouldRejectOverlappingWorkingHourRanges()
    {
        SysRole practitioner = buildPractitionerRole();
        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setRoles(Collections.singletonList(practitioner));

        when(userService.selectUserById(42L)).thenReturn(stored);

        Map<String, Object> firstRange = new HashMap<>();
        firstRange.put("start", "09:00");
        firstRange.put("end", "10:00");
        Map<String, Object> secondRange = new HashMap<>();
        secondRange.put("start", "09:30");
        secondRange.put("end", "10:30");
        Map<String, Object> workingHours = new HashMap<>();
        workingHours.put("monday", Arrays.asList(firstRange, secondRange));

        Map<String, Object> body = new HashMap<>();
        body.put("workingHours", workingHours);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> controller.update(42L, body));

        assertEquals("工作时间区间不能重叠: monday", ex.getMessage());
        verify(userService, never()).updateUserProfile(any());
        verify(userService, never()).updateUser(any());
    }

    @Test
    void update_shouldRejectUnparseableWorkingHourValue()
    {
        SysRole practitioner = buildPractitionerRole();
        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setRoles(Collections.singletonList(practitioner));

        when(userService.selectUserById(42L)).thenReturn(stored);

        Map<String, Object> mondayRange = new HashMap<>();
        mondayRange.put("start", "bogus");
        mondayRange.put("end", "10:00");
        Map<String, Object> workingHours = new HashMap<>();
        workingHours.put("monday", Collections.singletonList(mondayRange));

        Map<String, Object> body = new HashMap<>();
        body.put("workingHours", workingHours);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> controller.update(42L, body));

        assertEquals("工作时间时间格式无效: bogus", ex.getMessage());
        verify(userService, never()).updateUserProfile(any());
        verify(userService, never()).updateUser(any());
    }

    @Test
    void uploadPractitionerSignature_shouldAllowPractitionerSelfUpload() throws Exception
    {
        setLoginUser(42L, "practitioner");
        SysRole practitionerRole = buildPractitionerRole();
        SysUser stored = new SysUser();
        stored.setUserId(42L);
        stored.setNickName("Dr Chen");
        stored.setEmail("doctor@example.com");
        stored.setPhonenumber("4165550100");
        stored.setRoles(Collections.singletonList(practitionerRole));
        stored.setRemark("{\"title\":\"R.Ac\"}");

        when(userService.selectUserById(42L)).thenReturn(stored);
        when(hospitalFileStorage.store(any(MultipartFile.class), anyString()))
                .thenReturn("hospital-private/test/signature.png");
        when(signedFileUrlService.buildAccessUrl("hospital-private/test/signature.png"))
                .thenReturn("/api/public/files/access?resource=hospital-private/test/signature.png");

        MockMultipartFile file = new MockMultipartFile("file", "signature.png", "image/png", new byte[] { 1, 2, 3 });

        Map<String, Object> result = controller.uploadPractitionerSignature(42L, file);

        JSONObject returnedSignature = (JSONObject) result.get("signature");
        assertEquals("hospital-private/test/signature.png", returnedSignature.getString("path"));
        assertEquals("/api/public/files/access?resource=hospital-private/test/signature.png",
                returnedSignature.getString("url"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUserProfile(captor.capture());
        JSONObject savedProfile = JSONObject.parseObject(captor.getValue().getRemark());
        JSONObject savedSignature = savedProfile.getJSONObject("signature");
        assertEquals("R.Ac", savedProfile.getString("title"));
        assertEquals("hospital-private/test/signature.png", savedSignature.getString("path"));
    }

    @Test
    void uploadPractitionerSignature_shouldRejectOtherPractitioner()
    {
        setLoginUser(42L, "practitioner");
        MockMultipartFile file = new MockMultipartFile("file", "signature.png", "image/png", new byte[] { 1, 2, 3 });

        ServiceException ex = assertThrows(ServiceException.class,
                () -> controller.uploadPractitionerSignature(99L, file));

        assertEquals("无权修改其他用户签名", ex.getMessage());
        verify(userService, never()).selectUserById(99L);
        verify(userService, never()).updateUserProfile(any());
    }

    private SysRole buildPractitionerRole()
    {
        SysRole practitioner = new SysRole();
        practitioner.setRoleId(2L);
        practitioner.setRoleKey("practitioner");
        practitioner.setFlag(true);
        return practitioner;
    }

    private void setLoginUser(Long userId, String roleKey)
    {
        SysUser loginUserEntity = new SysUser();
        loginUserEntity.setUserId(userId);
        SysRole role = new SysRole();
        role.setRoleId("admin".equals(roleKey) ? 1L : 2L);
        role.setRoleKey(roleKey);
        role.setFlag(true);
        loginUserEntity.setRoles(Collections.singletonList(role));

        LoginUser loginUser = new LoginUser(loginUserEntity, Collections.emptySet());
        loginUser.setUserId(userId);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }
}
