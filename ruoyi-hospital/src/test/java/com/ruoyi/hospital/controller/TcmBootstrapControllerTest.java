package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.hospital.domain.TcmAcupoint;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmBranch;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmFormula;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.domain.TcmMeridian;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAcupointService;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmBranchService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmFormulaService;
import com.ruoyi.hospital.service.ITcmHerbDictService;
import com.ruoyi.hospital.service.ITcmMeridianService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmSettingsService;
import com.ruoyi.hospital.service.ITcmUnitConversionService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

@ExtendWith(MockitoExtension.class)
class TcmBootstrapControllerTest
{
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private ISysUserService sysUserService;

    @Mock
    private ISysRoleService roleService;

    @Mock
    private ITcmPatientService patientService;

    @Mock
    private ITcmAppointmentService appointmentService;

    @Mock
    private ITcmConsultationService consultationService;

    @Mock
    private ITcmBranchService branchService;

    @Mock
    private ITcmSettingsService settingsService;

    @Mock
    private ITcmFormulaService formulaService;

    @Mock
    private ITcmAcupointService acupointService;

    @Mock
    private ITcmUnitConversionService unitConversionService;

    @Mock
    private ITcmHerbDictService herbDictService;

    @Mock
    private ITcmMeridianService meridianService;

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void bootstrap_shouldSanitizePatientsForApprentice()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        loginAsApprentice(today.toString());

        TcmPatient patient = patient("p-1");
        TcmConsultation consultation = consultation("c-1", "p-1", today.toString());

        when(sysUserService.selectUserList(any(SysUser.class))).thenReturn(Collections.emptyList());
        when(patientService.selectTcmPatientList(any(TcmPatient.class))).thenReturn(List.of(patient));
        when(appointmentService.selectTcmAppointmentList(any(TcmAppointment.class))).thenReturn(Collections.emptyList());
        when(consultationService.selectTcmConsultationList(any(TcmConsultation.class))).thenReturn(List.of(consultation));
        when(branchService.selectTcmBranchList(any(TcmBranch.class))).thenReturn(Collections.emptyList());
        when(settingsService.getBundle()).thenReturn(Collections.emptyMap());
        when(formulaService.selectTcmFormulaList(any(TcmFormula.class))).thenReturn(Collections.emptyList());
        when(acupointService.selectTcmAcupointList(any(TcmAcupoint.class))).thenReturn(Collections.emptyList());
        when(unitConversionService.selectAll()).thenReturn(Collections.emptyList());
        when(herbDictService.selectTcmHerbDictList(any(TcmHerbDict.class))).thenReturn(Collections.emptyList());
        when(meridianService.selectTcmMeridianList(any(TcmMeridian.class))).thenReturn(Collections.emptyList());

        Map<String, Object> bootstrap = buildController().bootstrap();
        List<Map<String, Object>> patients = (List<Map<String, Object>>) bootstrap.get("patients");

        assertEquals(1, patients.size());
        assertEquals("p-1", patients.get(0).get("id"));
        assertEquals("Patient One", patients.get(0).get("name"));
        assertFalse(patients.get(0).containsKey("email"));
        assertFalse(patients.get(0).containsKey("phone"));
        assertFalse(patients.get(0).containsKey("emails"));
        assertFalse(patients.get(0).containsKey("mobilePhone"));
        assertFalse(patients.get(0).containsKey("notes"));
        assertFalse(patients.get(0).containsKey("historyAndMedication"));
        assertFalse(patients.get(0).containsKey("addressStreet"));
        assertFalse(patients.get(0).containsKey("consentSigned"));
    }

    private TcmBootstrapController buildController()
    {
        TcmBootstrapController controller = new TcmBootstrapController();
        ReflectionTestUtils.setField(controller, "sysUserService", sysUserService);
        ReflectionTestUtils.setField(controller, "roleService", roleService);
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        ReflectionTestUtils.setField(controller, "appointmentService", appointmentService);
        ReflectionTestUtils.setField(controller, "consultationService", consultationService);
        ReflectionTestUtils.setField(controller, "branchService", branchService);
        ReflectionTestUtils.setField(controller, "settingsService", settingsService);
        ReflectionTestUtils.setField(controller, "formulaService", formulaService);
        ReflectionTestUtils.setField(controller, "acupointService", acupointService);
        ReflectionTestUtils.setField(controller, "unitConversionService", unitConversionService);
        ReflectionTestUtils.setField(controller, "herbDictService", herbDictService);
        ReflectionTestUtils.setField(controller, "meridianService", meridianService);
        return controller;
    }

    private void loginAsApprentice(String internshipDate)
    {
        SysUser user = new SysUser();
        user.setUserId(88L);
        user.setRemark("{\"internshipDates\":[\"" + internshipDate + "\"]}");

        SysRole role = new SysRole();
        role.setRoleId(7L);
        role.setRoleKey("apprentice");
        role.setFlag(true);
        user.setRoles(List.of(role));

        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        loginUser.setUserId(88L);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private TcmPatient patient(String id)
    {
        TcmPatient patient = new TcmPatient();
        patient.setId(id);
        patient.setName("Patient One");
        patient.setFirstName("One");
        patient.setLastName("Patient");
        patient.setEmail("patient@example.com");
        patient.setPhone("123456");
        patient.setPractitionerId("doctor-1");
        patient.setIsActive(1);
        patient.setPayload(
                "{\"emails\":[\"patient@example.com\"],\"mobilePhone\":\"555-0001\",\"notes\":\"sensitive note\",\"historyAndMedication\":\"sensitive history\",\"addressStreet\":\"123 Main St\"}");
        return patient;
    }

    private TcmConsultation consultation(String id, String patientId, String consultDate)
    {
        TcmConsultation consultation = new TcmConsultation();
        consultation.setId(id);
        consultation.setPatientId(patientId);
        consultation.setPractitionerId("doctor-1");
        consultation.setConsultDate(consultDate);
        consultation.setStatus("completed");
        return consultation;
    }
}
