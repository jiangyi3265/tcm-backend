package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmPatientService;

@ExtendWith(MockitoExtension.class)
class TcmCoreListLoadingTest
{
    @Mock private ITcmPatientService patientService;
    @Mock private ITcmConsultationService consultationService;
    @Mock private ITcmAppointmentService appointmentService;

    @AfterEach
    void clearLogin()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminPatientListReadsOnlyPatients()
    {
        loginAs("admin");
        when(patientService.selectTcmPatientList(any(TcmPatient.class)))
                .thenReturn(List.of(patient("other", "102")));

        assertEquals(List.of("other"), ids(wire(new TcmPatientController()).list()));

        verify(patientService).selectTcmPatientList(any(TcmPatient.class));
        verifyNoInteractions(consultationService, appointmentService);
    }

    @Test
    void adminConsultationListReadsOnlyConsultations()
    {
        loginAs("admin");
        when(consultationService.selectTcmConsultationList(any(TcmConsultation.class)))
                .thenReturn(List.of(consultation("other", "unrelated", today())));

        assertEquals(List.of("other"), ids(wire(new TcmConsultationController()).list()));

        verify(consultationService).selectTcmConsultationList(any(TcmConsultation.class));
        verifyNoInteractions(patientService, appointmentService);
    }

    @Test
    void adminAppointmentListReadsAppointmentsOnceWithoutRelatedTables()
    {
        loginAs("admin");
        when(appointmentService.selectTcmAppointmentList(any(TcmAppointment.class)))
                .thenReturn(List.of(appointment("other", "unrelated", "102")));

        assertEquals(List.of("other"), ids(wire(new TcmAppointmentController()).list()));

        verify(appointmentService).selectTcmAppointmentList(any(TcmAppointment.class));
        verifyNoInteractions(patientService, consultationService);
    }

    @Test
    void practitionerPatientListStillFiltersUnrelatedPatientsAndHidesContacts()
    {
        clinicalFixture();

        List<Map<String, Object>> rows = wire(new TcmPatientController()).list();

        assertEquals(List.of("own", "shared"), ids(rows));
        assertFalse(rows.stream().anyMatch(row -> "qa@example.com".equals(row.get("email"))));
        verifyClinicalListsReadOnce();
    }

    @Test
    void practitionerConsultationListKeepsSamePatientAndDateRestrictions()
    {
        clinicalFixture();

        List<Map<String, Object>> rows = wire(new TcmConsultationController()).list();

        assertEquals(List.of("own-visit", "shared-recent"), ids(rows));
        verifyClinicalListsReadOnce();
    }

    @Test
    void practitionerAppointmentListReusesOneSnapshotWithoutExposingUnrelatedAppointments()
    {
        clinicalFixture();

        List<Map<String, Object>> rows = wire(new TcmAppointmentController()).list();

        assertEquals(List.of("own-appointment", "shared-appointment"), ids(rows));
        verifyClinicalListsReadOnce();
    }

    private void clinicalFixture()
    {
        loginAs("practitioner");
        when(patientService.selectTcmPatientList(any(TcmPatient.class)))
                .thenReturn(List.of(patient("own", "101"), patient("shared", "102"), patient("hidden", "102")));
        when(consultationService.selectTcmConsultationList(any(TcmConsultation.class)))
                .thenReturn(List.of(consultation("own-visit", "own", today().minusMonths(6)),
                        consultation("shared-recent", "shared", today()),
                        consultation("shared-old", "shared", today().minusMonths(6)),
                        consultation("hidden-visit", "hidden", today())));
        when(appointmentService.selectTcmAppointmentList(any(TcmAppointment.class)))
                .thenReturn(List.of(appointment("own-appointment", "own", "102"),
                        appointment("shared-appointment", "shared", "101"),
                        appointment("hidden-appointment", "hidden", "102")));
    }

    private void verifyClinicalListsReadOnce()
    {
        verify(patientService).selectTcmPatientList(any(TcmPatient.class));
        verify(consultationService).selectTcmConsultationList(any(TcmConsultation.class));
        verify(appointmentService).selectTcmAppointmentList(any(TcmAppointment.class));
    }

    private <T> T wire(T controller)
    {
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        ReflectionTestUtils.setField(controller, "consultationService", consultationService);
        ReflectionTestUtils.setField(controller, "appointmentService", appointmentService);
        return controller;
    }

    private void loginAs(String roleKey)
    {
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        SysUser user = new SysUser();
        user.setUserId(101L);
        user.setRoles(List.of(role));
        LoginUser login = new LoginUser();
        login.setUser(user);
        login.setUserId(101L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, List.of()));
    }

    private LocalDate today()
    {
        return LocalDate.now(ZoneId.of("Asia/Shanghai"));
    }

    private TcmPatient patient(String id, String practitionerId)
    {
        TcmPatient patient = new TcmPatient();
        patient.setId(id);
        patient.setName("Synthetic QA");
        patient.setPractitionerId(practitionerId);
        patient.setPayload("{\"email\":\"qa@example.com\"}");
        return patient;
    }

    private TcmConsultation consultation(String id, String patientId, LocalDate date)
    {
        TcmConsultation consultation = new TcmConsultation();
        consultation.setId(id);
        consultation.setPatientId(patientId);
        consultation.setPractitionerId("102");
        consultation.setConsultDate(date.toString());
        consultation.setStatus("completed");
        consultation.setPayload("{}");
        return consultation;
    }

    private TcmAppointment appointment(String id, String patientId, String practitionerId)
    {
        TcmAppointment appointment = new TcmAppointment();
        appointment.setId(id);
        appointment.setPatientId(patientId);
        appointment.setPractitionerId(practitionerId);
        appointment.setStartTime(today() + " 10:00:00");
        appointment.setEndTime(today() + " 11:00:00");
        appointment.setStatus("booked");
        appointment.setPayload("{}");
        return appointment;
    }

    private List<Object> ids(List<Map<String, Object>> rows)
    {
        return rows.stream().map(row -> row.get("id")).collect(Collectors.toList());
    }
}
