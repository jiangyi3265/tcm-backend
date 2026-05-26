package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.eq;
import static org.mockito.ArgumentMatchers.any;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmPdfService;

@ExtendWith(MockitoExtension.class)
class TcmConsultationControllerTest
{
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private ITcmConsultationService consultationService;

    @Mock
    private ITcmPatientService patientService;

    @Mock
    private ITcmAppointmentService appointmentService;

    @Mock
    private ITcmPdfService pdfService;

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldOnlyReturnConsultationsInsideActiveInternshipWindow()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        loginAsApprentice(today.minusDays(1).toString());

        TcmConsultation visible = consultation("c-1", "p-1", today.toString());
        TcmConsultation hidden = consultation("c-2", "p-1", today.minusDays(5).toString());

        when(patientService.selectTcmPatientList(org.mockito.ArgumentMatchers.any(TcmPatient.class)))
                .thenReturn(Collections.singletonList(patient("p-1")));
        when(consultationService.selectTcmConsultationList(org.mockito.ArgumentMatchers.any(TcmConsultation.class)))
                .thenReturn(Arrays.asList(visible, hidden));
        when(appointmentService.selectTcmAppointmentList(org.mockito.ArgumentMatchers.any(TcmAppointment.class)))
                .thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = buildController().list();

        assertEquals(1, result.size());
        assertEquals("c-1", result.get(0).get("id"));
    }

    @Test
    void get_shouldRejectConsultationOutsideActiveInternshipWindow()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        loginAsApprentice(today.toString());

        TcmConsultation hidden = consultation("c-2", "p-1", today.minusDays(4).toString());

        when(consultationService.selectTcmConsultationById("c-2")).thenReturn(hidden);
        when(appointmentService.selectTcmAppointmentList(org.mockito.ArgumentMatchers.any(TcmAppointment.class)))
                .thenReturn(Collections.emptyList());

        ServiceException error = assertThrows(ServiceException.class, () -> buildController().get("c-2"));

        assertEquals("access denied", error.getMessage());
    }

    @Test
    void create_shouldAllowPractitionerToCreateFirstConsultationForUnassignedPatient()
    {
        loginAsPractitioner(101L);
        TcmPatient unassigned = patient("p-new");
        unassigned.setPractitionerId(null);
        TcmConsultation saved = consultation("consult-new", "p-new", LocalDate.now(CLINIC_ZONE).toString());
        saved.setPractitionerId("101");

        when(patientService.selectTcmPatientById("p-new")).thenReturn(unassigned);
        when(consultationService.selectTcmConsultationList(any(TcmConsultation.class)))
                .thenReturn(Collections.emptyList());
        when(appointmentService.selectTcmAppointmentList(any(TcmAppointment.class)))
                .thenReturn(Collections.emptyList());
        when(consultationService.selectTcmConsultationById("consult-new")).thenReturn(saved);

        Map<String, Object> body = new HashMap<>();
        body.put("id", "consult-new");
        body.put("patientId", "p-new");
        body.put("practitionerId", "other-doctor");
        body.put("date", LocalDate.now(CLINIC_ZONE).toString());

        Map<String, Object> result = buildController().create(body);

        assertEquals("consult-new", result.get("id"));
        ArgumentCaptor<TcmConsultation> captor = ArgumentCaptor.forClass(TcmConsultation.class);
        verify(consultationService).insertTcmConsultation(captor.capture());
        assertEquals("101", captor.getValue().getPractitionerId());
    }

    @Test
    void reactivate_shouldUseStoredDatabaseIdWhenRouteUsesBusinessId()
    {
        loginAsPractitioner(101L);
        TcmConsultation existing = consultation("db-id", "p-1", LocalDate.now(CLINIC_ZONE).toString());
        existing.setConsultationId("ORD-ABC-123");
        existing.setPractitionerId("101");
        existing.setStatus("completed");
        existing.setLockedAt("2026-05-26 10:00:00");
        TcmPatient patient = patient("p-1");
        patient.setPractitionerId("101");

        when(consultationService.selectTcmConsultationById("ORD-ABC-123")).thenReturn(existing);
        when(patientService.selectTcmPatientById("p-1")).thenReturn(patient);
        when(appointmentService.selectTcmAppointmentList(any(TcmAppointment.class)))
                .thenReturn(Collections.emptyList());
        when(consultationService.reactivateConsultation(eq("db-id"), eq("101"))).thenReturn(existing);

        Map<String, Object> result = buildController().reactivate("ORD-ABC-123");

        assertEquals("db-id", result.get("id"));
        verify(consultationService).reactivateConsultation("db-id", "101");
    }

    private TcmConsultationController buildController()
    {
        TcmConsultationController controller = new TcmConsultationController();
        ReflectionTestUtils.setField(controller, "consultationService", consultationService);
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        ReflectionTestUtils.setField(controller, "appointmentService", appointmentService);
        ReflectionTestUtils.setField(controller, "pdfService", pdfService);
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
        user.setRoles(Collections.singletonList(role));

        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        loginUser.setUserId(88L);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private void loginAsPractitioner(Long userId)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);

        SysRole role = new SysRole();
        role.setRoleId(2L);
        role.setRoleKey("practitioner");
        role.setFlag(true);
        user.setRoles(Collections.singletonList(role));

        LoginUser loginUser = new LoginUser(user, Collections.emptySet());
        loginUser.setUserId(userId);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private TcmPatient patient(String id)
    {
        TcmPatient patient = new TcmPatient();
        patient.setId(id);
        patient.setName("patient-" + id);
        patient.setPractitionerId("doctor-1");
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
