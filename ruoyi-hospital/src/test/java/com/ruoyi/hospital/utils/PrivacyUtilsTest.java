package com.ruoyi.hospital.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
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
    void practitionerShouldSeeOnlyRecentConsultationsWithinThreeDays()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        TcmPatient patient = patient("patient-1", "other-practitioner");
        TcmConsultation recent = consultation("patient-1", today.minusDays(2).toString(), "completed", null);
        TcmConsultation old = consultation("patient-1", today.minusDays(5).toString(), "completed", null);

        assertTrue(PrivacyUtils.canAccessPatient(patient, Arrays.asList(recent, old)));

        Set<String> accessible = PrivacyUtils.collectAccessiblePatientIds(
                Collections.singletonList(patient),
                Arrays.asList(recent, old));

        assertTrue(accessible.contains("patient-1"));
    }

    @Test
    void practitionerShouldNotSeeConsultationsOlderThanThreeDays()
    {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        TcmPatient patient = patient("patient-2", "other-practitioner");
        TcmConsultation old = consultation("patient-2", today.minusDays(5).toString(), "completed", null);

        assertFalse(PrivacyUtils.canAccessPatient(patient, Collections.singletonList(old)));

        Set<String> accessible = PrivacyUtils.collectAccessiblePatientIds(
                Collections.singletonList(patient),
                Collections.singletonList(old));

        assertFalse(accessible.contains("patient-2"));
    }

    @Test
    void pharmacistVisibilityShouldStillRespectPaidConsultations()
    {
        setLoginUser(202L, "pharmacist");
        TcmPatient patient = patient("patient-3", "other-practitioner");
        TcmConsultation paid = consultation("patient-3", "2026-03-01 10:00:00", "completed", "paid");

        assertTrue(PrivacyUtils.canAccessPatient(patient, Collections.singletonList(paid)));
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

    private TcmConsultation consultation(String patientId, String consultDate, String status, String paymentStatus)
    {
        TcmConsultation consultation = new TcmConsultation();
        consultation.setPatientId(patientId);
        consultation.setPractitionerId("other-practitioner");
        consultation.setConsultDate(consultDate);
        consultation.setStatus(status);
        if (paymentStatus != null)
        {
            consultation.setPayload("{\"paymentStatus\":\"" + paymentStatus + "\"}");
        }
        return consultation;
    }
}
