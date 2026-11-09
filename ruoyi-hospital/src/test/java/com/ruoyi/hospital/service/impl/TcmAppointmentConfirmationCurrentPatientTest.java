package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.*;
import com.ruoyi.system.mapper.SysUserMapper;

/** Models an earlier MySQL read view explicitly; H2 itself is not an InnoDB RR simulation. */
class TcmAppointmentConfirmationCurrentPatientTest
{
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private TcmPatient oldReadView;
    private TcmPatientMapper patientMapper;
    private TcmAppointmentMapper appointmentMapper;
    private TcmPatientServiceImpl patients;
    private TcmAppointmentNotificationServiceImpl notifications;
    private ITcmEmailService email;

    @BeforeEach void setup()
    {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:confirmation_current_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE patients (id VARCHAR(64) PRIMARY KEY, body VARCHAR(100000))");
        jdbc.execute("CREATE TABLE appointments (id VARCHAR(64) PRIMARY KEY, payload VARCHAR(100000))");
        oldReadView = new TcmPatient(); oldReadView.setId("patient"); oldReadView.setName("Synthetic Patient");
        oldReadView.setEmail("before@example.invalid"); oldReadView.setConsentSigned(0); oldReadView.setIsActive(1);
        oldReadView.setConsentToken("old-token"); oldReadView.setPayload("{}");
        TcmPatient current = JSON.parseObject(JSON.toJSONString(oldReadView), TcmPatient.class);
        current.setEmail("current@example.invalid"); current.setConsentSigned(1); current.setConsentToken(null);
        current.setPayload("{\"latestIntakeCompleted\":true,\"latestIntakeSource\":\"public_intake_form\",\"consentSignatureName\":\"Synthetic Patient\"}");
        jdbc.update("INSERT INTO patients VALUES (?, ?)", current.getId(), JSON.toJSONString(current));
        jdbc.update("INSERT INTO appointments VALUES ('appointment', '{\"manageToken\":\"manage\"}')");
        patientMapper = mock(TcmPatientMapper.class);
        // Ordinary reads deliberately retain the old snapshot after a locking read.
        when(patientMapper.selectTcmPatientById("patient")).thenAnswer(call -> JSON.parseObject(JSON.toJSONString(oldReadView), TcmPatient.class));
        when(patientMapper.selectTcmPatientByIdForUpdate("patient")).thenAnswer(call ->
                JSON.parseObject(jdbc.queryForObject("SELECT body FROM patients WHERE id='patient' FOR UPDATE", String.class), TcmPatient.class));
        patients = spy(new TcmPatientServiceImpl());
        ReflectionTestUtils.setField(patients, "tcmPatientMapper", patientMapper);
        appointmentMapper = mock(TcmAppointmentMapper.class);
        when(appointmentMapper.selectTcmAppointmentById("appointment")).thenAnswer(call -> appointment());
        when(appointmentMapper.selectTcmAppointmentForUpdate("appointment")).thenAnswer(call -> {
            jdbc.queryForObject("SELECT id FROM appointments WHERE id='appointment' FOR UPDATE", String.class);
            return appointment();
        });
        when(appointmentMapper.updateNotificationPayload(any(), any())).thenAnswer(call -> {
            TcmAppointment update = call.getArgument(0), expected = call.getArgument(1);
            return jdbc.update("UPDATE appointments SET payload=? WHERE id=? AND payload=?", update.getPayload(), update.getId(), expected.getPayload());
        });
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        transaction = new TransactionTemplate(manager);
        email = mock(ITcmEmailService.class);
        when(email.sendTemplateAndLog(anyString(), anyString(), any(), anyString(), anyString(), anyString())).thenReturn(true);
        notifications = new TcmAppointmentNotificationServiceImpl();
        ReflectionTestUtils.setField(notifications, "transactionManager", manager);
        ReflectionTestUtils.setField(notifications, "appointmentMapper", appointmentMapper);
        ReflectionTestUtils.setField(notifications, "patientService", patients);
        ReflectionTestUtils.setField(notifications, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(notifications, "emailService", email);
        ReflectionTestUtils.setField(notifications, "branchService", mock(ITcmBranchService.class));
        ReflectionTestUtils.setField(notifications, "roomService", mock(ITcmRoomService.class));
        ReflectionTestUtils.setField(notifications, "serviceTypeService", mock(ITcmServiceTypeService.class));
        ReflectionTestUtils.setField(notifications, "clinicSettingMapper", mock(TcmClinicSettingMapper.class));
        ReflectionTestUtils.setField(notifications, "userMapper", mock(SysUserMapper.class));
        ReflectionTestUtils.setField(notifications, "publicAppBaseUrl", "https://test.invalid");
    }

    @Test void newlySignedPatientDoesNotCauseConfirmationToRegenerateConsentAndFailTheBooking()
    {
        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> {
            assertEquals(0, patients.selectTcmPatientById("patient").getConsentSigned());
            assertEquals(1, patientMapper.selectTcmPatientByIdForUpdate("patient").getConsentSigned());
            notifications.handleAppointmentCreated(appointment());
            verifyNoInteractions(email);
        }));
        verify(patients, never()).generateConsentToken(anyString());
        verify(appointmentMapper, never()).updateNotificationIntake(any(), any());
        verify(email).sendTemplateAndLog(eq("current@example.invalid"), eq("appointmentConfirmation"), any(), anyString(), anyString(), anyString());
        assertEquals(1, JSON.parseObject(jdbc.queryForObject("SELECT body FROM patients", String.class), TcmPatient.class).getConsentSigned());
    }

    @Test void confirmationUsesCurrentContactInsteadOfTheEarlierReadView()
    {
        oldReadView.setConsentSigned(1);
        oldReadView.setPayload("{\"latestIntakeCompleted\":true,\"latestIntakeSource\":\"public_intake_form\"}");
        transaction.executeWithoutResult(status -> {
            assertEquals("before@example.invalid", patients.selectTcmPatientById("patient").getEmail());
            assertEquals("current@example.invalid", patientMapper.selectTcmPatientByIdForUpdate("patient").getEmail());
            notifications.handleAppointmentCreated(appointment());
        });
        verify(email).sendTemplateAndLog(eq("current@example.invalid"), eq("appointmentConfirmation"), any(), anyString(), anyString(), anyString());
        verify(email, never()).sendTemplateAndLog(eq("before@example.invalid"), anyString(), any(), anyString(), anyString(), anyString());
    }

    private TcmAppointment appointment()
    {
        TcmAppointment appointment = new TcmAppointment(); appointment.setId("appointment"); appointment.setPatientId("patient");
        appointment.setStatus("booked"); appointment.setServiceType("acupuncture");
        appointment.setStartTime("2026-11-15 09:00:00"); appointment.setEndTime("2026-11-15 09:30:00");
        appointment.setPayload(jdbc.queryForObject("SELECT payload FROM appointments WHERE id='appointment'", String.class));
        return appointment;
    }
}
