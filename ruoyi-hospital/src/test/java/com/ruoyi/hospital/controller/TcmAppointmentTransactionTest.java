package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmAppointmentNotificationService;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.utils.PayloadUtils;

/** Real controllers and H2 commits; service substitutes persist through Spring-bound JDBC. */
class TcmAppointmentTransactionTest
{
    private JdbcDataSource source;
    private JdbcTemplate jdbc;
    private final TcmPatientMapper patientMapper = mock(TcmPatientMapper.class);
    private TcmAppointmentController controller;
    private DataSourceTransactionManager manager;
    private final ITcmPatientService patients = mock(ITcmPatientService.class);
    private final ITcmAppointmentService appointments = mock(ITcmAppointmentService.class);
    private final ITcmAppointmentNotificationService notifications = mock(ITcmAppointmentNotificationService.class);
    private final ITcmConsultationService consultations = mock(ITcmConsultationService.class);
    private final ITcmAuditLogService audit = mock(ITcmAuditLogService.class);
    private final AtomicInteger dispatched = new AtomicInteger();
    private boolean preparedInTransaction;

    @BeforeEach void setup()
    {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000");
        jdbc = new JdbcTemplate(source);
        manager = new DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE patients (id VARCHAR(64) PRIMARY KEY, body CLOB)");
        jdbc.execute("CREATE TABLE appointments (id VARCHAR(64) PRIMARY KEY, body CLOB)");
        jdbc.execute("CREATE TABLE notification_events (appointment_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE audit_events (appointment_id VARCHAR(64))");
        when(patients.selectTcmPatientById(anyString())).thenAnswer(call -> patient(call.getArgument(0)));
        when(patients.selectTcmPatientList(any())).thenAnswer(call -> patientRows());
        when(patientMapper.selectTcmPatientByIdForUpdate(anyString())).thenAnswer(call -> {
            List<TcmPatient> rows = jdbc.query("SELECT body FROM patients WHERE id = ? FOR UPDATE",
                    (rs, index) -> JSON.parseObject(rs.getString(1), TcmPatient.class), (String) call.getArgument(0));
            return rows.isEmpty() ? null : rows.get(0);
        });
        when(patients.insertTcmPatient(any())).thenAnswer(call -> {
            TcmPatient patient = call.getArgument(0);
            return jdbc.update("INSERT INTO patients VALUES (?, ?)", patient.getId(), JSON.toJSONString(patient));
        });
        when(patients.updateTcmPatient(any())).thenAnswer(call -> {
            TcmPatient patient = call.getArgument(0);
            return jdbc.update("UPDATE patients SET body = ? WHERE id = ?", JSON.toJSONString(patient), patient.getId());
        });
        when(appointments.selectTcmAppointmentById(anyString())).thenAnswer(call -> appointment(call.getArgument(0)));
        when(appointments.selectTcmAppointmentList(any())).thenAnswer(call -> appointmentRows());
        when(appointments.insertTcmAppointment(any())).thenAnswer(call -> insertAppointment(call.getArgument(0)));
        when(appointments.updateTcmAppointment(any())).thenAnswer(call -> {
            TcmAppointment appointment = call.getArgument(0);
            return jdbc.update("UPDATE appointments SET body = ? WHERE id = ?", JSON.toJSONString(appointment), appointment.getId());
        });
        when(appointments.updateStatus(anyString(), anyString())).thenAnswer(call -> {
            TcmAppointment appointment = appointment(call.getArgument(0));
            appointment.setStatus(call.getArgument(1));
            appointments.updateTcmAppointment(appointment);
            return appointment;
        });
        when(consultations.selectTcmConsultationList(any())).thenReturn(Collections.emptyList());
        doAnswer(call -> { prepareNotification(call.getArgument(0)); return null; })
                .when(notifications).handleAppointmentCreated(any());
        doAnswer(call -> { prepareNotification(call.getArgument(1)); return null; })
                .when(notifications).handleAppointmentUpdated(any(), any());
        doAnswer(call -> { prepareNotification(call.getArgument(1)); return null; })
                .when(notifications).handleAppointmentStatusChanged(any(), any());
        doAnswer(call -> { jdbc.update("INSERT INTO audit_events VALUES (?)", (String) call.getArgument(1)); return null; })
                .when(audit).log(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        controller = new TcmAppointmentController();
        ReflectionTestUtils.setField(controller, "patientService", patients);
        ReflectionTestUtils.setField(controller, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(controller, "appointmentService", appointments);
        ReflectionTestUtils.setField(controller, "appointmentNotificationService", notifications);
        ReflectionTestUtils.setField(controller, "consultationService", consultations);
        ReflectionTestUtils.setField(controller, "auditLogService", audit);
        ReflectionTestUtils.setField(controller, "transactionManager", manager);
        TcmPatient patient = new TcmPatient();
        patient.setId("alex"); patient.setName("Lee Alex"); patient.setFirstName("Alex"); patient.setLastName("Lee");
        patient.setEmail("family@example.invalid"); patient.setIsActive(1); patient.setPayload("{}");
        patients.insertTcmPatient(patient);
        loginAsAdmin();
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void createNotificationFailureRollsBackAppointmentAndPractitionerAssignment()
    {
        doAnswer(call -> { prepareNotification(call.getArgument(0)); throw failure(); })
                .when(notifications).handleAppointmentCreated(any());
        assertThrows(ServiceException.class, () -> controller.create(request("new", "2026-11-11 09:00:00")));
        assertEquals(0, count("appointments"));
        assertNull(patient("alex").getPractitionerId());
        assertEquals(0, count("notification_events"));
        assertEquals(0, dispatched.get());
    }

    @Test void createPractitionerFailureRollsBackAppointment()
    {
        doThrow(failure()).when(patients).updateTcmPatient(any());
        assertThrows(ServiceException.class, () -> controller.create(request("new", "2026-11-11 09:00:00")));
        assertEquals(0, count("appointments"));
        verifyNoInteractions(notifications);
    }

    @Test void rescheduleNotificationFailurePreservesOriginalAppointment()
    {
        appointments.insertTcmAppointment(PayloadUtils.toAppointment(request("existing", "2026-11-11 09:00:00")));
        String before = JSON.toJSONString(appointment("existing"));
        doAnswer(call -> { prepareNotification(call.getArgument(1)); throw failure(); })
                .when(notifications).handleAppointmentUpdated(any(), any());
        assertThrows(ServiceException.class, () -> controller.update("existing", request("existing", "2026-11-11 10:00:00")));
        assertEquals(before, JSON.toJSONString(appointment("existing")));
        assertNull(patient("alex").getPractitionerId());
        assertEquals(0, count("notification_events"));
        assertEquals(0, dispatched.get());
    }

    @Test void cancellationNotificationFailurePreservesBookedStatus()
    {
        appointments.insertTcmAppointment(PayloadUtils.toAppointment(request("existing", "2026-11-11 09:00:00")));
        doAnswer(call -> { prepareNotification(call.getArgument(1)); throw failure(); })
                .when(notifications).handleAppointmentStatusChanged(any(), any());
        assertThrows(ServiceException.class, () -> controller.updateStatus("existing", Collections.singletonMap("status", "cancelled")));
        assertEquals("booked", appointment("existing").getStatus());
        assertEquals(0, count("notification_events"));
        assertEquals(0, dispatched.get());
    }

    @Test void auditFailureDoesNotCommitOrDispatchPreparedNotification()
    {
        doThrow(failure()).when(audit).log(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertThrows(ServiceException.class, () -> controller.create(request("new", "2026-11-11 09:00:00")));
        assertEquals(0, count("appointments"));
        assertEquals(0, count("notification_events"));
        assertNull(patient("alex").getPractitionerId());
        assertEquals(0, dispatched.get());
    }

    @Test void successfulCreateCommitsBeforeDispatch()
    {
        controller.create(request("new", "2026-11-11 09:00:00"));
        assertTrue(preparedInTransaction);
        assertEquals(1, count("appointments"));
        assertEquals(1, count("notification_events"));
        assertEquals(1, count("audit_events"));
        assertEquals("doctor", patient("alex").getPractitionerId());
        assertEquals(1, dispatched.get());
    }

    @Test void successfulChangesCommitBeforeDispatch()
    {
        appointments.insertTcmAppointment(PayloadUtils.toAppointment(request("existing", "2026-11-11 09:00:00")));
        controller.update("existing", request("existing", "2026-11-11 10:00:00"));
        assertTrue(preparedInTransaction);
        assertEquals("2026-11-11 10:00:00", appointment("existing").getStartTime());
        preparedInTransaction = false;
        controller.updateStatus("existing", Collections.singletonMap("status", "cancelled"));
        assertTrue(preparedInTransaction);
        assertEquals("cancelled", appointment("existing").getStatus());
        assertEquals(2, count("notification_events"));
        assertEquals(2, count("audit_events"));
        assertEquals(2, dispatched.get());
    }

    @Test void publicSlotCheckWaitsForInternalCommitAndLosingBookingLeavesNoPatient() throws Exception
    {
        CountDownLatch firstAtCommit = new CountDownLatch(1), releaseCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1), secondAvailabilityRead = new CountDownLatch(1);
        AtomicInteger commits = new AtomicInteger(), availabilityReads = new AtomicInteger();
        DataSourceTransactionManager gatedManager = new DataSourceTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status) {
                if (commits.incrementAndGet() == 1) {
                    firstAtCommit.countDown();
                    try { assertTrue(releaseCommit.await(5, TimeUnit.SECONDS)); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                }
                super.doCommit(status);
            }
        };
        ReflectionTestUtils.setField(controller, "transactionManager", gatedManager);
        TcmPublicBookingController publicController = new TcmPublicBookingController();
        ReflectionTestUtils.setField(publicController, "transactionManager", gatedManager);
        ReflectionTestUtils.setField(publicController, "patientService", patients);
        ReflectionTestUtils.setField(publicController, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(publicController, "appointmentService", appointments);
        ReflectionTestUtils.setField(publicController, "appointmentNotificationService", notifications);
        doAnswer(call -> {
            if (availabilityReads.incrementAndGet() > 1) secondAvailabilityRead.countDown();
            return insertAppointment(call.getArgument(0));
        }).when(appointments).insertTcmAppointment(any());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> first = pool.submit(() -> {
                loginAsAdmin();
                try { return controller.create(request("internal", "2026-11-11 09:00:00")); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(firstAtCommit.await(5, TimeUnit.SECONDS));
            assertEquals(0, count("appointments"), "The first appointment is still uncommitted at the gate");
            Future<Map<String, Object>> second = pool.submit(() -> {
                Map<String, Object> body = request("public", "2026-11-11 09:00:00");
                body.put("firstName", "Beatrice"); body.put("lastName", "Lee");
                body.put("email", "family@example.invalid"); body.put("phone", "5550100");
                secondStarted.countDown();
                return publicController.create(body);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertFalse(secondAvailabilityRead.await(300, TimeUnit.MILLISECONDS),
                    "Public availability must not run until the internal transaction has committed");
            assertFalse(second.isDone());
            releaseCommit.countDown();
            assertEquals("internal", first.get(5, TimeUnit.SECONDS).get("id"));
            ExecutionException rejected = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
            assertTrue(rejected.getCause() instanceof ServiceException);
            assertEquals("appointment slot is unavailable", rejected.getCause().getMessage());
            assertEquals(2, availabilityReads.get());
            assertEquals(1, commits.get());
            assertEquals(1, count("appointments"));
            assertEquals(1, count("patients"), "The losing public request must roll back its new household member");
            assertEquals("Lee Alex", patient("alex").getName());
            assertEquals(1, dispatched.get());
        } finally { releaseCommit.countDown(); pool.shutdownNow(); }
    }

    @Test void primaryAssignmentPreservesTheCurrentSignedProfileDespiteAnOlderOrdinaryReadView()
    {
        TcmPatient stale = patient("alex");
        TcmPatient signed = patient("alex"); signed.setConsentSigned(1); signed.setPayload("{\"version\":\"signed\"}");
        patients.updateTcmPatient(signed);
        // Simulate MySQL RR ordinary reads retaining an earlier view; current read uses actual H2 row.
        doReturn(stale).when(patients).selectTcmPatientById("alex");
        controller.create(request("signed-profile", "2026-11-11 10:00:00"));
        assertEquals(1, patient("alex").getConsentSigned());
        assertTrue(patient("alex").getPayload().contains("signed"));
        assertEquals("doctor", patient("alex").getPractitionerId());
    }

    private int insertAppointment(TcmAppointment candidate)
    {
        if (appointmentRows().stream().anyMatch(existing -> !"cancelled".equals(existing.getStatus())
                && candidate.getStartTime().equals(existing.getStartTime()))) throw new ServiceException("appointment slot is unavailable");
        return jdbc.update("INSERT INTO appointments VALUES (?, ?)", candidate.getId(), JSON.toJSONString(candidate));
    }

    private void prepareNotification(TcmAppointment appointment)
    {
        jdbc.update("INSERT INTO notification_events VALUES (?)", appointment.getId());
        preparedInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        Runnable dispatch = () -> {
            try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
                    ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM appointments")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
                dispatched.incrementAndGet();
            } catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatch.run(); }
            });
        } else dispatch.run();
    }

    private List<TcmPatient> patientRows() { return jdbc.query("SELECT body FROM patients", (rs, index) -> JSON.parseObject(rs.getString(1), TcmPatient.class)); }
    private List<TcmAppointment> appointmentRows() { return jdbc.query("SELECT body FROM appointments", (rs, index) -> JSON.parseObject(rs.getString(1), TcmAppointment.class)); }
    private TcmPatient patient(String id) { return patientRows().stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null); }
    private TcmAppointment appointment(String id) { return appointmentRows().stream().filter(row -> id.equals(row.getId())).findFirst().orElse(null); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private ServiceException failure() { return new ServiceException("Synthetic notification/persistence preparation failure"); }

    private Map<String, Object> request(String id, String start)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id); body.put("patientId", "alex"); body.put("practitionerId", "doctor");
        body.put("serviceType", "acupuncture"); body.put("startTime", start);
        body.put("endTime", start.replace(":00:00", ":30:00")); body.put("status", "booked");
        return body;
    }

    private void loginAsAdmin()
    {
        SysRole role = new SysRole(); role.setRoleId(1L); role.setRoleKey("admin");
        SysUser user = new SysUser(); user.setUserId(1L); user.setRoles(Collections.singletonList(role));
        LoginUser login = new LoginUser(user, Collections.emptySet()); login.setUserId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null, Collections.emptyList()));
    }
}
