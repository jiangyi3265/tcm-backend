package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmEmailLogMapper;
import com.ruoyi.hospital.service.*;
import com.ruoyi.system.mapper.SysUserMapper;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

/** Actual Spring commit callbacks and persisted claims; SMTP and executor are isolated. */
class TcmAppointmentNotificationCommitTest
{
    private final Instant now = Instant.parse("2026-10-13T17:00:00Z");
    private final AtomicBoolean failPayloadWrite = new AtomicBoolean();
    private JdbcTemplate jdbc;
    private JdbcTemplate independentJdbc;
    private TransactionTemplate transaction;
    private TcmAppointmentNotificationServiceImpl service;
    private ITcmEmailService email;
    private TcmAppointment template;

    @BeforeEach void setup()
    {
        String databaseUrl = "jdbc:h2:mem:notification_commit_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        DriverManagerDataSource source = new DriverManagerDataSource(databaseUrl, "sa", "");
        jdbc = new JdbcTemplate(source);
        independentJdbc = new JdbcTemplate(new DriverManagerDataSource(databaseUrl, "sa", ""));
        jdbc.execute("CREATE TABLE notification_state (id VARCHAR(50) PRIMARY KEY, payload VARCHAR(100000))");
        jdbc.execute("CREATE TABLE booking_commit (id VARCHAR(50) PRIMARY KEY)");
        jdbc.update("INSERT INTO notification_state VALUES (?, ?)", "event", "{\"manageToken\":\"manage\"}");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        transaction = new TransactionTemplate(manager);

        template = new TcmAppointment(); template.setId("event"); template.setPatientId("patient");
        template.setStatus("booked"); template.setServiceType("consultation");
        template.setStartTime("2026-10-20 10:00:00"); template.setEndTime("2026-10-20 11:00:00");
        TcmAppointmentMapper mapper = mock(TcmAppointmentMapper.class);
        when(mapper.selectTcmAppointmentById("event")).thenAnswer(call -> current());
        when(mapper.selectTcmAppointmentForUpdate("event")).thenAnswer(call -> {
            jdbc.queryForObject("SELECT id FROM notification_state WHERE id='event' FOR UPDATE", String.class);
            return current();
        });
        when(mapper.selectTcmAppointmentList(any())).thenAnswer(call -> List.of(current()));
        when(mapper.updateNotificationPayload(any(), any())).thenAnswer(call -> {
            if (failPayloadWrite.get()) throw new DataAccessResourceFailureException("synthetic storage outage");
            TcmAppointment update = call.getArgument(0), expected = call.getArgument(1);
            return jdbc.update("UPDATE notification_state SET payload=? WHERE id=? AND payload=?",
                    update.getPayload(), update.getId(), expected.getPayload());
        });
        ITcmPatientService patients = mock(ITcmPatientService.class);
        TcmPatient patient = new TcmPatient(); patient.setId("patient"); patient.setName("Synthetic Patient");
        patient.setEmail("synthetic@example.invalid"); patient.setConsentSigned(1);
        patient.setPayload("{\"latestIntakeCompleted\":true,\"latestIntakeSource\":\"public_intake_form\"}");
        when(patients.selectTcmPatientById("patient")).thenReturn(patient);
        email = mock(ITcmEmailService.class);
        when(email.sendTemplateAndLog(anyString(), anyString(), any(), anyString(), anyString(), anyString())).thenReturn(true);

        service = new TcmAppointmentNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "transactionManager", manager);
        ReflectionTestUtils.setField(service, "appointmentMapper", mapper);
        ReflectionTestUtils.setField(service, "patientService", patients);
        ReflectionTestUtils.setField(service, "emailService", email);
        ReflectionTestUtils.setField(service, "branchService", mock(ITcmBranchService.class));
        ReflectionTestUtils.setField(service, "roomService", mock(ITcmRoomService.class));
        ReflectionTestUtils.setField(service, "serviceTypeService", mock(ITcmServiceTypeService.class));
        ReflectionTestUtils.setField(service, "clinicSettingMapper", mock(TcmClinicSettingMapper.class));
        ReflectionTestUtils.setField(service, "userMapper", mock(SysUserMapper.class));
        ReflectionTestUtils.setField(service, "publicAppBaseUrl", "https://test.invalid");
        setClock(now);
    }

    @Test void rejectedEventDispatchAndCleanupFailureDoNotFailAnAlreadyCommittedBooking()
    {
        rejectExecutorAndFailCleanup();
        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO booking_commit VALUES ('committed')");
            service.handleAppointmentCreated(current());
            verifyNoInteractions(email);
        }));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM booking_commit", Integer.class));
        JSONObject event = payload().getJSONObject("notificationOutbox").getJSONObject("confirmationEmailSentAt");
        assertNotNull(event.getString("pendingToken"));
        assertFalse(payload().containsKey("confirmationEmailSentAt"));
        verifyNoInteractions(email);

        recoverAndRunDueNotifications();
        verify(email, times(1)).sendTemplateAndLog(anyString(), eq("appointmentConfirmation"), any(), anyString(), anyString(), anyString());
        assertTrue(payload().containsKey("confirmationEmailSentAt"));
        assertTrue(payload().getJSONObject("notificationOutbox").isEmpty());
    }

    @Test void rejectedCareDispatchAndCleanupFailureDoNotFailAnAlreadyCommittedUpdate()
    {
        template.setStatus("completed");
        template.setStartTime("2026-10-13 09:00:00"); template.setEndTime("2026-10-13 10:00:00");
        jdbc.update("UPDATE notification_state SET payload=? WHERE id='event'",
                "{\"manageToken\":\"manage\",\"treatmentCompletedAt\":\"2026-10-13 10:00:00\",\"followUpEmailSentAt\":\"already\"}");
        rejectExecutorAndFailCleanup();
        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO booking_commit VALUES ('committed')");
            service.processDueNotifications();
            verifyNoInteractions(email);
        }));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM booking_commit", Integer.class));
        assertNotNull(payload().getString("aftercareEmailSentAtPendingToken"));
        assertFalse(payload().containsKey("aftercareEmailSentAt"));
        verifyNoInteractions(email);

        recoverAndRunDueNotifications();
        verify(email, times(1)).sendTemplateAndLog(anyString(), eq("aftercare"), any(), anyString(), anyString(), anyString());
        assertTrue(payload().containsKey("aftercareEmailSentAt"));
        assertFalse(payload().containsKey("aftercareEmailSentAtPendingToken"));
    }

    @Test void notificationPreparationFailureStillRollsBackTheBooking()
    {
        failPayloadWrite.set(true);
        assertThrows(DataAccessResourceFailureException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO booking_commit VALUES ('must-rollback')");
            service.handleAppointmentCreated(current());
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM booking_commit", Integer.class));
        assertFalse(payload().containsKey("notificationOutbox"));
        verifyNoInteractions(email);
    }

    @Test void inlineExecutorPersistsDeliveryStateOutsideTheCommittedTransaction()
    {
        AtomicBoolean ranInline = new AtomicBoolean();
        ReflectionTestUtils.setField(service, "notificationTaskExecutor", (TaskExecutor) task -> {
            ranInline.set(true);
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                    "CallerRuns dispatch must not inherit the already-committed request transaction");
            task.run();
            JSONObject independentlyStored = JSONObject.parseObject(independentJdbc.queryForObject(
                    "SELECT payload FROM notification_state WHERE id='event'", String.class));
            assertTrue(independentlyStored.containsKey("confirmationEmailSentAt"),
                    "A completed delivery must be visible from a separate database connection");
        });
        transaction.executeWithoutResult(status -> service.handleAppointmentCreated(current()));
        assertTrue(ranInline.get());
        service.processDueNotifications();
        verify(email, times(1)).sendTemplateAndLog(anyString(), eq("appointmentConfirmation"), any(), anyString(), anyString(), anyString());
    }

    @Test void rollbackDoesNotDispatchPreparedNotification()
    {
        Queue<Runnable> tasks = new ArrayDeque<>();
        ReflectionTestUtils.setField(service, "notificationTaskExecutor", (TaskExecutor) tasks::add);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            service.handleAppointmentCreated(current());
            throw new IllegalStateException("synthetic later failure");
        }));
        assertTrue(tasks.isEmpty());
        assertFalse(payload().containsKey("notificationOutbox"));
        verifyNoInteractions(email);
    }

    @Test void acceptedEventEmailWithLogOutageIsPersistedAndNotSentAgain()
    {
        JavaMailSender sender = useRealEmailWithFailedDeliveryLog();

        transaction.executeWithoutResult(status -> service.handleAppointmentCreated(current()));
        setClock(now.plusSeconds(11 * 60));
        service.processDueNotifications();

        verify(sender, times(1)).send(any(MimeMessage.class));
        JSONObject independentlyStored = JSONObject.parseObject(independentJdbc.queryForObject(
                "SELECT payload FROM notification_state WHERE id='event'", String.class));
        assertTrue(independentlyStored.containsKey("confirmationEmailSentAt"));
        assertTrue(independentlyStored.getJSONObject("notificationOutbox").isEmpty());
    }

    @Test void acceptedCareEmailWithLogOutageIsPersistedAndNotSentAgain()
    {
        template.setStatus("completed");
        template.setStartTime("2026-10-13 09:00:00"); template.setEndTime("2026-10-13 10:00:00");
        jdbc.update("UPDATE notification_state SET payload=? WHERE id='event'",
                "{\"manageToken\":\"manage\",\"treatmentCompletedAt\":\"2026-10-13 10:00:00\",\"followUpEmailSentAt\":\"already\"}");
        JavaMailSender sender = useRealEmailWithFailedDeliveryLog();

        transaction.executeWithoutResult(status -> service.processDueNotifications());
        setClock(now.plusSeconds(11 * 60));
        service.processDueNotifications();

        verify(sender, times(1)).send(any(MimeMessage.class));
        JSONObject independentlyStored = JSONObject.parseObject(independentJdbc.queryForObject(
                "SELECT payload FROM notification_state WHERE id='event'", String.class));
        assertTrue(independentlyStored.containsKey("aftercareEmailSentAt"));
        assertFalse(independentlyStored.containsKey("aftercareEmailSentAtPendingToken"));
    }

    private JavaMailSender useRealEmailWithFailedDeliveryLog()
    {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        TcmEmailLogMapper logs = mock(TcmEmailLogMapper.class);
        when(logs.insertTcmEmailLog(any())).thenThrow(new DataAccessResourceFailureException("synthetic log outage"));
        TcmEmailServiceImpl realEmail = new TcmEmailServiceImpl();
        ReflectionTestUtils.setField(realEmail, "mailSender", sender);
        ReflectionTestUtils.setField(realEmail, "fromAddress", "synthetic@example.invalid");
        ReflectionTestUtils.setField(realEmail, "emailLogMapper", logs);
        ReflectionTestUtils.setField(realEmail, "clinicSettingMapper", mock(TcmClinicSettingMapper.class));
        ReflectionTestUtils.setField(service, "emailService", realEmail);
        return sender;
    }

    private void rejectExecutorAndFailCleanup()
    {
        ReflectionTestUtils.setField(service, "notificationTaskExecutor", (TaskExecutor) task -> {
            failPayloadWrite.set(true);
            throw new RejectedExecutionException("synthetic executor unavailable");
        });
    }

    private void recoverAndRunDueNotifications()
    {
        failPayloadWrite.set(false);
        setClock(now.plusSeconds(11 * 60));
        Queue<Runnable> tasks = new ArrayDeque<>();
        ReflectionTestUtils.setField(service, "notificationTaskExecutor", (TaskExecutor) tasks::add);
        service.processDueNotifications();
        assertEquals(1, tasks.size());
        while (!tasks.isEmpty()) tasks.remove().run();
        service.processDueNotifications();
        assertTrue(tasks.isEmpty(), "A successful notification must not be queued a second time");
    }

    private void setClock(Instant instant)
    {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(instant, ZoneId.of("America/Toronto")));
    }

    private TcmAppointment current()
    {
        TcmAppointment result = JSONObject.parseObject(JSONObject.toJSONString(template), TcmAppointment.class);
        result.setPayload(jdbc.queryForObject("SELECT payload FROM notification_state WHERE id='event'", String.class));
        return result;
    }

    private JSONObject payload() { return JSONObject.parseObject(current().getPayload()); }
}
