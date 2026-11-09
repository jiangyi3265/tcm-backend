package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmBranch;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.*;
import com.ruoyi.system.mapper.SysUserMapper;

class TcmAppointmentNotificationLockOrderTest
{
    private static final String OLD_START = "2026-11-10 09:00:00";
    private static final String NEW_START = "2026-11-10 10:00:00";

    @Test void parallelEventWorkersPersistBothSentMarkersWithoutRetryingEitherDelivery() throws Exception
    {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:notification_workers_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE appointments (id VARCHAR(64) PRIMARY KEY, start_time VARCHAR(30), end_time VARCHAR(30), payload VARCHAR(100000))");
        jdbc.update("INSERT INTO appointments VALUES (?, ?, ?, ?)", "appointment", OLD_START, "2026-11-10 09:30:00", "{\"manageToken\":\"manage\"}");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        Queue<Runnable> queued = new ConcurrentLinkedQueue<>();
        CountDownLatch bothDelivered = new CountDownLatch(2), secondMarkerAttempt = new CountDownLatch(1);
        AtomicInteger markerAttempts = new AtomicInteger(), failedCas = new AtomicInteger();
        TcmAppointmentMapper mapper = mock(TcmAppointmentMapper.class);
        when(mapper.selectTcmAppointmentById("appointment")).thenAnswer(call -> current(jdbc));
        when(mapper.selectTcmAppointmentList(any())).thenAnswer(call -> List.of(current(jdbc)));
        when(mapper.selectTcmAppointmentForUpdate("appointment")).thenAnswer(call -> {
            int attempt = bothDelivered.getCount() == 0 ? markerAttempts.incrementAndGet() : 0;
            if (attempt == 2) secondMarkerAttempt.countDown();
            jdbc.queryForObject("SELECT id FROM appointments WHERE id='appointment' FOR UPDATE", String.class);
            // The first sent-marker transaction holds the row until the other worker
            // actually tries to lock it, so this proves serialization of fresh payloads.
            if (attempt == 1) assertTrue(secondMarkerAttempt.await(3, TimeUnit.SECONDS));
            return current(jdbc);
        });
        when(mapper.updateNotificationPayload(any(), any())).thenAnswer(call -> {
            TcmAppointment updated = call.getArgument(0), expected = call.getArgument(1);
            int written = jdbc.update("UPDATE appointments SET payload=? WHERE id=? AND payload=?",
                    updated.getPayload(), updated.getId(), expected.getPayload());
            if (written == 0) failedCas.incrementAndGet();
            return written;
        });
        ITcmPatientService patients = mock(ITcmPatientService.class);
        TcmPatient patient = new TcmPatient(); patient.setId("patient"); patient.setName("Synthetic Patient");
        patient.setEmail("synthetic@example.invalid"); patient.setConsentSigned(1);
        patient.setPayload("{\"latestIntakeCompleted\":true,\"latestIntakeSource\":\"public_intake_form\"}");
        when(patients.selectTcmPatientById("patient")).thenReturn(patient);
        ITcmBranchService branches = mock(ITcmBranchService.class);
        TcmBranch branch = new TcmBranch(); branch.setEmail("internal@example.invalid");
        when(branches.selectTcmBranchById("branch")).thenReturn(branch);
        ITcmEmailService email = mock(ITcmEmailService.class);
        when(email.sendTemplateAndLog(anyString(), anyString(), any(), anyString(), anyString(), anyString())).thenAnswer(call -> {
            bothDelivered.countDown();
            assertTrue(bothDelivered.await(3, TimeUnit.SECONDS));
            return true;
        });
        TcmAppointmentNotificationServiceImpl service = new TcmAppointmentNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "transactionManager", manager);
        ReflectionTestUtils.setField(service, "appointmentMapper", mapper);
        ReflectionTestUtils.setField(service, "patientService", patients);
        TcmPatientMapper patientMapper = mock(TcmPatientMapper.class);
        when(patientMapper.selectTcmPatientByIdForUpdate("patient")).thenReturn(patient);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "emailService", email);
        ReflectionTestUtils.setField(service, "branchService", branches);
        ReflectionTestUtils.setField(service, "roomService", mock(ITcmRoomService.class));
        ReflectionTestUtils.setField(service, "serviceTypeService", mock(ITcmServiceTypeService.class));
        ReflectionTestUtils.setField(service, "clinicSettingMapper", mock(TcmClinicSettingMapper.class));
        ReflectionTestUtils.setField(service, "userMapper", mock(SysUserMapper.class));
        ReflectionTestUtils.setField(service, "publicAppBaseUrl", "https://test.invalid");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-11-01T16:00:00Z"), ZoneId.of("America/Toronto")));
        ReflectionTestUtils.setField(service, "notificationTaskExecutor", (TaskExecutor) queued::add);
        service.handleAppointmentCreated(current(jdbc));
        assertEquals(2, queued.size());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(queued.remove()), second = pool.submit(queued.remove());
            first.get(6, TimeUnit.SECONDS); second.get(6, TimeUnit.SECONDS);
            assertTrue(markerAttempts.get() >= 2);
            assertEquals(0, failedCas.get(), "Both workers must update the latest row payload under its database lock");
            JSONObject payload = JSONObject.parseObject(current(jdbc).getPayload());
            assertTrue(payload.containsKey("confirmationEmailSentAt"));
            assertTrue(payload.containsKey("internalBookingEmailSentAt"));
            assertTrue(payload.getJSONObject("notificationOutbox").isEmpty());
            service.processDueNotifications();
            assertTrue(queued.isEmpty());
            verify(email, times(1)).sendTemplateAndLog(anyString(), eq("appointmentConfirmation"), any(), anyString(), anyString(), anyString());
            verify(email, times(1)).sendTemplateAndLog(anyString(), eq("internalBooking"), any(), anyString(), anyString(), anyString());
        } finally { pool.shutdownNow(); }
    }

    @Test void schedulerWaitingForAnAppointmentRowDoesNotBlockTheTransactionsNotificationPreparation() throws Exception
    {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:notification_lock_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=750", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE appointments (id VARCHAR(64) PRIMARY KEY, start_time VARCHAR(30), end_time VARCHAR(30), payload VARCHAR(100000))");
        jdbc.update("INSERT INTO appointments VALUES (?, ?, ?, ?)", "appointment", OLD_START, "2026-11-10 09:30:00", "{\"manageToken\":\"manage\"}");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        CountDownLatch rowLocked = new CountDownLatch(1), schedulerAttemptedWrite = new CountDownLatch(1);
        AtomicReference<DataAccessException> blockedWriteFailure = new AtomicReference<>();
        AtomicReference<Thread> schedulerThread = new AtomicReference<>();
        Queue<Runnable> queuedEmails = new ConcurrentLinkedQueue<>();
        TcmAppointmentMapper mapper = mock(TcmAppointmentMapper.class);
        when(mapper.selectTcmAppointmentById("appointment")).thenAnswer(call -> current(jdbc));
        when(mapper.selectTcmAppointmentForUpdate("appointment")).thenAnswer(call -> {
            if (Thread.currentThread() == schedulerThread.get()) schedulerAttemptedWrite.countDown();
            try {
                jdbc.queryForObject("SELECT id FROM appointments WHERE id='appointment' FOR UPDATE", String.class);
                return current(jdbc);
            } catch (DataAccessException failure) { blockedWriteFailure.compareAndSet(null, failure); throw failure; }
        });
        when(mapper.selectTcmAppointmentList(any())).thenAnswer(call -> List.of(current(jdbc)));
        when(mapper.updateNotificationPayload(any(), any())).thenAnswer(call -> {
            TcmAppointment updated = call.getArgument(0), expected = call.getArgument(1);
            if (OLD_START.equals(expected.getStartTime())) schedulerAttemptedWrite.countDown();
            try {
                return jdbc.update("UPDATE appointments SET payload=? WHERE id=? AND start_time=? AND end_time=? AND payload=?",
                        updated.getPayload(), updated.getId(), expected.getStartTime(), expected.getEndTime(), expected.getPayload());
            } catch (DataAccessException failure) { blockedWriteFailure.compareAndSet(null, failure); throw failure; }
        });
        ITcmPatientService patients = mock(ITcmPatientService.class);
        TcmPatient patient = new TcmPatient(); patient.setId("patient"); patient.setName("Synthetic Patient");
        patient.setEmail("synthetic@example.invalid"); patient.setConsentSigned(1);
        patient.setPayload("{\"latestIntakeCompleted\":true,\"latestIntakeSource\":\"public_intake_form\"}");
        when(patients.selectTcmPatientById("patient")).thenReturn(patient);
        ITcmEmailService email = mock(ITcmEmailService.class);
        when(email.sendTemplateAndLog(anyString(), anyString(), any(), anyString(), anyString(), anyString())).thenReturn(true);
        TcmAppointmentNotificationServiceImpl service = new TcmAppointmentNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "transactionManager", manager);
        ReflectionTestUtils.setField(service, "appointmentMapper", mapper);
        ReflectionTestUtils.setField(service, "patientService", patients);
        TcmPatientMapper patientMapper = mock(TcmPatientMapper.class);
        when(patientMapper.selectTcmPatientByIdForUpdate("patient")).thenReturn(patient);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "emailService", email);
        ReflectionTestUtils.setField(service, "branchService", mock(ITcmBranchService.class));
        ReflectionTestUtils.setField(service, "roomService", mock(ITcmRoomService.class));
        ReflectionTestUtils.setField(service, "serviceTypeService", mock(ITcmServiceTypeService.class));
        ReflectionTestUtils.setField(service, "clinicSettingMapper", mock(TcmClinicSettingMapper.class));
        ReflectionTestUtils.setField(service, "userMapper", mock(SysUserMapper.class));
        ReflectionTestUtils.setField(service, "publicAppBaseUrl", "https://test.invalid");
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-11-09T16:00:00Z"), ZoneId.of("America/Toronto")));
        ReflectionTestUtils.setField(service, "notificationTaskExecutor", (TaskExecutor) queuedEmails::add);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> edited = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                TcmAppointment before = current(jdbc);
                jdbc.queryForObject("SELECT id FROM appointments WHERE id='appointment' FOR UPDATE", String.class);
                jdbc.update("UPDATE appointments SET start_time=?, end_time=? WHERE id='appointment'", NEW_START, "2026-11-10 10:30:00");
                rowLocked.countDown();
                try { assertTrue(schedulerAttemptedWrite.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                service.handleAppointmentUpdated(before, current(jdbc));
            }));
            assertTrue(rowLocked.await(5, TimeUnit.SECONDS));
            Future<?> scheduled = pool.submit(() -> { schedulerThread.set(Thread.currentThread()); service.processDueNotifications(); });
            edited.get(8, TimeUnit.SECONDS);
            scheduled.get(8, TimeUnit.SECONDS);
            assertNull(blockedWriteFailure.get(), "A JVM claim lock must not keep the row-owning transaction waiting until the scheduler's DB lock timeout");
            assertEquals(NEW_START, current(jdbc).getStartTime());
            while (!queuedEmails.isEmpty()) queuedEmails.remove().run();
            verify(email, times(1)).sendTemplateAndLog(anyString(), eq("appointmentChange"), any(), anyString(), anyString(), anyString());
            verify(email, never()).sendTemplateAndLog(anyString(), eq("reminder"), any(), anyString(), anyString(), anyString());
            assertFalse(JSONObject.parseObject(current(jdbc).getPayload()).containsKey("reminderEmailSentAt"));
        } finally { pool.shutdownNow(); }
    }

    private TcmAppointment current(JdbcTemplate jdbc)
    {
        return jdbc.queryForObject("SELECT start_time,end_time,payload FROM appointments WHERE id='appointment'", (rs, index) -> {
            TcmAppointment appointment = new TcmAppointment(); appointment.setId("appointment"); appointment.setPatientId("patient");
            appointment.setBranchId("branch");
            appointment.setStatus("booked"); appointment.setServiceType("acupuncture");
            appointment.setStartTime(rs.getString(1)); appointment.setEndTime(rs.getString(2)); appointment.setPayload(rs.getString(3));
            return appointment;
        });
    }
}
