package com.ruoyi.hospital.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;

class PrivacyUtilsTest
{
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

    @BeforeEach
    void setUp()
    {
        setLoginUser(101L, "practitioner");
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void primaryPractitionerShouldSeeAllPatientConsultations()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        TcmPatient patient = patient("patient-1", "101");
        TcmConsultation oldByOther = consultation("c-old", "patient-1", "other-practitioner",
                today.minusMonths(6).toString(), "completed", null);

        assertTrue(PrivacyUtils.canAccessPatient(patient, Collections.singletonList(oldByOther)));

        List<TcmConsultation> visible = PrivacyUtils.filterConsultations(
                Collections.singletonList(oldByOther),
                Collections.singletonList(patient),
                Collections.emptyList());

        assertEquals(1, visible.size());
        assertEquals("c-old", visible.get(0).getId());
    }

    @Test
    void nonPrimaryPractitionerShouldNotSeeOtherRecordsWithoutAppointment()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        TcmPatient patient = patient("patient-2", "other-practitioner");
        TcmConsultation recentByOther = consultation("c-recent", "patient-2", "other-practitioner",
                today.minusDays(2).toString(), "completed", null);

        assertFalse(PrivacyUtils.canAccessPatient(patient, Collections.singletonList(recentByOther)));

        Set<String> accessible = PrivacyUtils.collectAccessiblePatientIds(
                Collections.singletonList(patient),
                Collections.singletonList(recentByOther));

        assertFalse(accessible.contains("patient-2"));
    }

    @Test
    void appointmentPractitionerShouldSeeOnlyRecentThreeMonthsOfOtherRecords()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        TcmPatient patient = patient("patient-3", "other-practitioner");
        TcmConsultation recentByOther = consultation("c-recent", "patient-3", "other-practitioner",
                today.minusMonths(2).toString(), "completed", null);
        TcmConsultation oldByOther = consultation("c-old", "patient-3", "other-practitioner",
                today.minusMonths(4).toString(), "completed", null);
        TcmAppointment appointment = appointment("patient-3", "101", today);

        assertTrue(PrivacyUtils.canAccessPatient(
                patient,
                Arrays.asList(recentByOther, oldByOther),
                Collections.singletonList(appointment)));

        List<TcmConsultation> visible = PrivacyUtils.filterConsultations(
                Arrays.asList(recentByOther, oldByOther),
                Collections.singletonList(patient),
                Collections.singletonList(appointment));

        assertEquals(1, visible.size());
        assertEquals("c-recent", visible.get(0).getId());
    }

    @Test
    void expiredAppointmentShouldHideOtherRecordsButKeepOwnRecords()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        TcmPatient patient = patient("patient-4", "other-practitioner");
        TcmConsultation otherRecord = consultation("c-other", "patient-4", "other-practitioner",
                today.minusDays(1).toString(), "completed", null);
        TcmConsultation ownRecord = consultation("c-own", "patient-4", "101",
                today.minusMonths(8).toString(), "completed", null);
        TcmAppointment expiredAppointment = appointment("patient-4", "101", today.minusDays(8));

        assertTrue(PrivacyUtils.canAccessPatient(
                patient,
                Arrays.asList(otherRecord, ownRecord),
                Collections.singletonList(expiredAppointment)));

        List<TcmConsultation> visible = PrivacyUtils.filterConsultations(
                Arrays.asList(otherRecord, ownRecord),
                Collections.singletonList(patient),
                Collections.singletonList(expiredAppointment));

        assertEquals(1, visible.size());
        assertEquals("c-own", visible.get(0).getId());
    }

    @Test
    void adminWithPractitionerRoleShouldHaveFullPatientAccess()
    {
        setLoginUser(101L, "admin", "practitioner");
        TcmPatient patient = patient("patient-5", "other-practitioner");

        assertTrue(PrivacyUtils.canAccessPatient(patient, Collections.emptyList()));

        Set<String> accessible = PrivacyUtils.collectAccessiblePatientIds(
                Collections.singletonList(patient),
                Collections.emptyList());

        assertTrue(accessible.contains("patient-5"));
    }

    @Test
    void pharmacistVisibilityShouldStillRespectPaidConsultations()
    {
        setLoginUser(202L, "pharmacist");
        TcmPatient patient = patient("patient-6", "other-practitioner");
        TcmConsultation paid = consultation("c-paid", "patient-6", "other-practitioner",
                "2026-03-01 10:00:00", "completed", "paid");

        assertTrue(PrivacyUtils.canAccessPatient(patient, Collections.singletonList(paid)));
    }

    private void setLoginUser(Long userId, String... roleKeys)
    {
        SysUser loginUserEntity = new SysUser();
        loginUserEntity.setUserId(userId);
        List<SysRole> roles = new ArrayList<>();
        for (String roleKey : roleKeys)
        {
            SysRole role = new SysRole();
            role.setRoleId(1L);
            role.setRoleKey(roleKey);
            role.setFlag(true);
            roles.add(role);
        }
        loginUserEntity.setRoles(roles);

        LoginUser loginUser = new LoginUser(loginUserEntity, Collections.emptySet());
        loginUser.setUserId(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private TcmPatient patient(String id, String practitionerId)
    {
        TcmPatient patient = new TcmPatient();
        patient.setId(id);
        patient.setPractitionerId(practitionerId);
        return patient;
    }

    private TcmConsultation consultation(
            String id,
            String patientId,
            String practitionerId,
            String consultDate,
            String status,
            String paymentStatus)
    {
        TcmConsultation consultation = new TcmConsultation();
        consultation.setId(id);
        consultation.setPatientId(patientId);
        consultation.setPractitionerId(practitionerId);
        consultation.setConsultDate(consultDate);
        consultation.setStatus(status);
        if (paymentStatus != null)
        {
            consultation.setPayload("{\"paymentStatus\":\"" + paymentStatus + "\"}");
        }
        return consultation;
    }

    private TcmAppointment appointment(String patientId, String practitionerId, LocalDate date)
    {
        TcmAppointment appointment = new TcmAppointment();
        appointment.setPatientId(patientId);
        appointment.setPractitionerId(practitionerId);
        appointment.setStatus("completed");
        appointment.setStartTime(date + " 10:00:00");
        appointment.setEndTime(date + " 10:30:00");
        return appointment;
    }
}
