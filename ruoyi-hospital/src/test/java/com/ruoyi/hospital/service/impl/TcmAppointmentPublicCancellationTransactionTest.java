package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.service.*;
import com.ruoyi.system.mapper.SysUserMapper;

class TcmAppointmentPublicCancellationTransactionTest
{
    private JdbcTemplate jdbc;
    private JdbcTemplate independent;
    private TcmAppointmentMapper mapper;
    private TcmAppointmentNotificationServiceImpl service;
    private ITcmEmailService email;
    private final AtomicBoolean failPreparation = new AtomicBoolean();
    private boolean cancellationWasTransactional;

    @BeforeEach void setup()
    {
        String url = "jdbc:h2:mem:public_cancel_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        DriverManagerDataSource source = new DriverManagerDataSource(url, "sa", "");
        jdbc = new JdbcTemplate(source);
        independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        jdbc.execute("CREATE TABLE appointments (id VARCHAR(64) PRIMARY KEY, status VARCHAR(30), intake_token VARCHAR(64), payload VARCHAR(100000))");
        jdbc.update("INSERT INTO appointments VALUES ('appointment', 'booked', 'intake', '{\"manageToken\":\"manage\"}')");
        mapper = mock(TcmAppointmentMapper.class);
        when(mapper.selectTcmAppointmentById("appointment")).thenAnswer(call -> current());
        when(mapper.selectTcmAppointmentByManageToken("manage")).thenAnswer(call -> current());
        when(mapper.selectTcmAppointmentByIntakeToken("intake")).thenAnswer(call -> current());
        when(mapper.selectTcmAppointmentForUpdate("appointment")).thenAnswer(call -> {
            jdbc.queryForObject("SELECT id FROM appointments WHERE id='appointment' FOR UPDATE", String.class);
            return current();
        });
        when(mapper.cancelNotificationAppointment(any(), any())).thenAnswer(call -> {
            cancellationWasTransactional = TransactionSynchronizationManager.isActualTransactionActive();
            TcmAppointment updated = call.getArgument(0), expected = call.getArgument(1);
            return jdbc.update("UPDATE appointments SET status='cancelled', payload=? WHERE id=? AND status=? AND intake_token=? AND payload=?",
                    updated.getPayload(), updated.getId(), expected.getStatus(), expected.getIntakeToken(), expected.getPayload());
        });
        when(mapper.updateNotificationPayload(any(), any())).thenAnswer(call -> {
            if (failPreparation.get()) throw new DataAccessResourceFailureException("Synthetic cancellation notification preparation failure");
            TcmAppointment updated = call.getArgument(0), expected = call.getArgument(1);
            return jdbc.update("UPDATE appointments SET payload=? WHERE id=? AND status=? AND intake_token=? AND payload=?",
                    updated.getPayload(), updated.getId(), expected.getStatus(), expected.getIntakeToken(), expected.getPayload());
        });
        ITcmPatientService patients = mock(ITcmPatientService.class);
        TcmPatient patient = new TcmPatient(); patient.setId("patient"); patient.setName("Synthetic Patient");
        patient.setEmail("synthetic@example.invalid");
        when(patients.selectTcmPatientById("patient")).thenReturn(patient);
        email = mock(ITcmEmailService.class);
        when(email.sendTemplateAndLog(anyString(), anyString(), any(), anyString(), anyString(), anyString())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "SMTP must execute outside the cancellation transaction");
            assertEquals("cancelled", independent.queryForObject("SELECT status FROM appointments", String.class));
            return true;
        });
        service = new TcmAppointmentNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "transactionManager", new DataSourceTransactionManager(source));
        ReflectionTestUtils.setField(service, "appointmentMapper", mapper);
        ReflectionTestUtils.setField(service, "patientService", patients);
        ReflectionTestUtils.setField(service, "emailService", email);
        ReflectionTestUtils.setField(service, "branchService", mock(ITcmBranchService.class));
        ReflectionTestUtils.setField(service, "roomService", mock(ITcmRoomService.class));
        ReflectionTestUtils.setField(service, "serviceTypeService", mock(ITcmServiceTypeService.class));
        ReflectionTestUtils.setField(service, "clinicSettingMapper", mock(TcmClinicSettingMapper.class));
        ReflectionTestUtils.setField(service, "userMapper", mock(SysUserMapper.class));
        ReflectionTestUtils.setField(service, "publicAppBaseUrl", "https://test.invalid");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-11-09T16:00:00Z"), ZoneId.of("America/Toronto")));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void preparationFailureRollsBackCancellationAndItsMetadata(boolean intake)
    {
        String before = current().getPayload();
        failPreparation.set(true);
        assertThrows(DataAccessResourceFailureException.class, () -> cancel(intake));
        assertEquals("booked", current().getStatus());
        assertEquals(before, current().getPayload());
        verifyNoInteractions(email);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void successfulCancellationCommitsBeforeSending(boolean intake)
    {
        assertEquals("cancelled", cancel(intake).getStatus());
        assertTrue(cancellationWasTransactional);
        assertTrue(JSONObject.parseObject(current().getPayload()).containsKey("cancelEmailSentAt"));
        verify(email, times(1)).sendTemplateAndLog(anyString(), eq("appointmentCancellation"), any(), anyString(), anyString(), anyString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void tokenRevokedAfterLookupCannotCancelTheCurrentAppointment(boolean intake)
    {
        TcmAppointment stale = current();
        if (intake) {
            doAnswer(call -> { jdbc.update("UPDATE appointments SET intake_token='replacement'"); return stale; })
                    .when(mapper).selectTcmAppointmentByIntakeToken("intake");
        } else {
            doAnswer(call -> { jdbc.update("UPDATE appointments SET payload='{\"manageToken\":\"replacement\"}'"); return stale; })
                    .when(mapper).selectTcmAppointmentByManageToken("manage");
        }
        assertThrows(ServiceException.class, () -> cancel(intake));
        assertEquals("booked", current().getStatus());
        verifyNoInteractions(email);
    }

    private TcmAppointment cancel(boolean intake)
    {
        return intake ? service.cancelByIntakeToken("intake", "patient_intake_form")
                : service.cancelByManageToken("manage", "patient_public");
    }

    private TcmAppointment current()
    {
        return jdbc.queryForObject("SELECT status,intake_token,payload FROM appointments WHERE id='appointment'", (rs, index) -> {
            TcmAppointment appointment = new TcmAppointment(); appointment.setId("appointment"); appointment.setPatientId("patient");
            appointment.setStatus(rs.getString(1)); appointment.setIntakeToken(rs.getString(2)); appointment.setPayload(rs.getString(3));
            appointment.setServiceType("acupuncture"); appointment.setStartTime("2026-11-10 09:00:00"); appointment.setEndTime("2026-11-10 09:30:00");
            return appointment;
        });
    }
}
