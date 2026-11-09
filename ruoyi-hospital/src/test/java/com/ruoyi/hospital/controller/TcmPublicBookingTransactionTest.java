package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAppointmentNotificationService;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmPatientService;

/** Real controller, Spring transaction manager and JDBC writes; no external systems. */
class TcmPublicBookingTransactionTest
{
    private JdbcDataSource source;
    private JdbcTemplate jdbc;
    private TcmPublicBookingController controller;
    private final ITcmPatientService patients = mock(ITcmPatientService.class);
    private final ITcmAppointmentService appointments = mock(ITcmAppointmentService.class);
    private final ITcmAppointmentNotificationService notifications = mock(ITcmAppointmentNotificationService.class);
    private final AtomicInteger dispatched = new AtomicInteger();
    private boolean notificationPreparedInTransaction;

    @BeforeEach void setup()
    {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE patients (id VARCHAR(64) PRIMARY KEY, body CLOB)");
        jdbc.execute("CREATE TABLE appointments (id VARCHAR(64) PRIMARY KEY, patient_id VARCHAR(64), body CLOB)");
        jdbc.execute("CREATE TABLE summaries (appointment_id VARCHAR(64), body CLOB)");
        jdbc.execute("CREATE TABLE notification_events (appointment_id VARCHAR(64))");
        when(patients.selectTcmPatientList(any())).thenAnswer(call -> patientRows());
        when(patients.selectTcmPatientById(anyString())).thenAnswer(call -> patient(call.getArgument(0)));
        when(patients.insertTcmPatient(any())).thenAnswer(call -> {
            TcmPatient patient = call.getArgument(0);
            return jdbc.update("INSERT INTO patients VALUES (?, ?)", patient.getId(), JSON.toJSONString(patient));
        });
        when(patients.updateTcmPatient(any())).thenAnswer(call -> {
            TcmPatient patient = call.getArgument(0);
            return jdbc.update("UPDATE patients SET body = ? WHERE id = ?", JSON.toJSONString(patient), patient.getId());
        });
        when(appointments.insertTcmAppointment(any())).thenAnswer(call -> persistAppointment(call.getArgument(0)));
        when(appointments.selectTcmAppointmentById(anyString())).thenAnswer(call -> {
            List<TcmAppointment> rows = jdbc.query("SELECT body FROM appointments WHERE id = ?",
                    (rs, index) -> JSON.parseObject(rs.getString(1), TcmAppointment.class), (String) call.getArgument(0));
            return rows.isEmpty() ? null : rows.get(0);
        });
        doAnswer(call -> {
            jdbc.update("INSERT INTO summaries VALUES (?, ?)", (String) call.getArgument(1), JSON.toJSONString(call.getArgument(2)));
            return null;
        }).when(patients).savePublicBookingIntakeSummary(anyString(), anyString(), anyMap());
        doAnswer(call -> { prepareNotification(call.getArgument(0)); return null; })
                .when(notifications).handleAppointmentCreated(any());

        TcmPublicBookingController target = new TcmPublicBookingController();
        ReflectionTestUtils.setField(target, "patientService", patients);
        ReflectionTestUtils.setField(target, "appointmentService", appointments);
        ReflectionTestUtils.setField(target, "appointmentNotificationService", notifications);
        ReflectionTestUtils.setField(target, "transactionManager", new DataSourceTransactionManager(source));
        controller = target;
    }

    @Test void unavailableSlotDoesNotLeaveANewPatient()
    {
        doThrow(new ServiceException("Slot unavailable")).when(appointments).insertTcmAppointment(any());
        assertThrows(ServiceException.class, () -> controller.create(request("conflict", "5550101")));
        assertEquals(0, count("patients"));
        assertEquals(0, count("appointments"));
        verifyNoInteractions(notifications);
    }

    @Test void unavailableSlotDoesNotChangeAnExistingPatientsContact()
    {
        TcmPatient original = originalPatient();
        patients.insertTcmPatient(original);
        doThrow(new ServiceException("Slot unavailable")).when(appointments).insertTcmAppointment(any());
        assertThrows(ServiceException.class, () -> controller.create(request("conflict", "5559999")));
        assertEquals("5550100", patient(original.getId()).getPhone());
        assertEquals(original.getPayload(), patient(original.getId()).getPayload());
        assertEquals(1, count("patients"));
        assertEquals(0, count("appointments"));
    }

    @Test void failedSummaryWriteRollsBackTheAppointmentAndAllowsRetry()
    {
        doThrow(new ServiceException("Synthetic summary persistence failure"))
                .when(patients).savePublicBookingIntakeSummary(anyString(), anyString(), anyMap());
        assertThrows(ServiceException.class, () -> controller.create(request("retry", "5550101")));
        assertEquals(0, count("appointments"));
        assertEquals(0, count("patients"));
        doNothing().when(patients).savePublicBookingIntakeSummary(anyString(), anyString(), anyMap());
        controller.create(request("retry", "5550101"));
        assertEquals(1, count("appointments"));
        assertEquals(1, count("patients"));
    }

    @Test void failedPractitionerAssignmentRollsBackTheNewAppointment()
    {
        doThrow(new ServiceException("Synthetic patient persistence failure")).when(patients).updateTcmPatient(any());
        assertThrows(ServiceException.class, () -> controller.create(request("failed-assignment", "5550101")));
        assertEquals(0, count("appointments"));
        assertEquals(0, count("patients"));
        verifyNoInteractions(notifications);
    }

    @Test void notificationPreparationFailureRollsBackAndDoesNotDispatch()
    {
        doAnswer(call -> {
            prepareNotification(call.getArgument(0));
            throw new ServiceException("Synthetic notification preparation failure");
        }).when(notifications).handleAppointmentCreated(any());
        assertThrows(ServiceException.class, () -> controller.create(request("failed-notification", "5550101")));
        assertEquals(0, count("appointments"));
        assertEquals(0, count("patients"));
        assertEquals(0, count("notification_events"));
        assertEquals(0, dispatched.get());
    }

    @Test void successfulBookingCommitsBeforeNotificationDispatch()
    {
        Map<String, Object> response = controller.create(request("success", "5550101"));
        assertNotNull(response.get("patientId"));
        assertTrue(notificationPreparedInTransaction);
        assertEquals(1, count("patients"));
        assertEquals(1, count("appointments"));
        assertEquals(1, count("notification_events"));
        assertEquals(1, dispatched.get());
    }

    @Test void concurrentSameIdentityLookupsWaitForTheEarlierTransactionToCommit() throws Exception
    {
        CountDownLatch firstAtCommit = new CountDownLatch(1), releaseCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1), secondLookup = new CountDownLatch(1);
        AtomicInteger lookups = new AtomicInteger(), commits = new AtomicInteger();
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
        doAnswer(call -> {
            if (lookups.incrementAndGet() > 1) secondLookup.countDown();
            return patientRows();
        }).when(patients).selectTcmPatientList(any());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> first = pool.submit(() -> controller.create(request("first", "5550101")));
            assertTrue(firstAtCommit.await(5, TimeUnit.SECONDS));
            assertEquals(0, count("patients"), "The first patient must still be uncommitted at the gate");
            Future<Map<String, Object>> second = pool.submit(() -> {
                secondStarted.countDown();
                return controller.create(request("second", "5550101"));
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertFalse(secondLookup.await(300, TimeUnit.MILLISECONDS),
                    "The identity lock must cover the transaction commit, not just the booking method body");
            assertFalse(second.isDone());
            releaseCommit.countDown();
            assertEquals(first.get(5, TimeUnit.SECONDS).get("patientId"), second.get(5, TimeUnit.SECONDS).get("patientId"));
            assertEquals(1, count("patients"));
            assertEquals(2, count("appointments"));
            assertEquals(2, commits.get());
        } finally { releaseCommit.countDown(); pool.shutdownNow(); }
    }

    private int persistAppointment(TcmAppointment appointment)
    {
        return jdbc.update("INSERT INTO appointments VALUES (?, ?, ?)", appointment.getId(), appointment.getPatientId(), JSON.toJSONString(appointment));
    }

    private void prepareNotification(TcmAppointment appointment)
    {
        jdbc.update("INSERT INTO notification_events VALUES (?)", appointment.getId());
        notificationPreparedInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        Runnable dispatch = () -> {
            // A separate connection observes only committed rows, unlike a thread-bound JDBC read.
            try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM appointments")) {
                assertTrue(result.next());
                assertTrue(result.getInt(1) > 0, "Notification must never observe an uncommitted appointment");
                dispatched.incrementAndGet();
            } catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { dispatch.run(); }
            });
        } else dispatch.run();
    }

    private List<TcmPatient> patientRows()
    {
        return jdbc.query("SELECT body FROM patients", (rs, index) -> JSON.parseObject(rs.getString(1), TcmPatient.class));
    }

    private TcmPatient patient(String id)
    {
        List<TcmPatient> rows = jdbc.query("SELECT body FROM patients WHERE id = ?",
                (rs, index) -> JSON.parseObject(rs.getString(1), TcmPatient.class), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    private TcmPatient originalPatient()
    {
        TcmPatient patient = new TcmPatient(); patient.setId("existing");
        patient.setFirstName("Alex"); patient.setLastName("Lee"); patient.setName("Lee Alex");
        patient.setEmail("family@example.test"); patient.setPhone("5550100"); patient.setIsActive(1);
        patient.setPayload("{\"medicalHistory\":\"Synthetic protected history\",\"emails\":[\"family@example.test\"]}");
        return patient;
    }

    private Map<String, Object> request(String id, String phone)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id); body.put("firstName", "Alex"); body.put("lastName", "Lee");
        body.put("email", "family@example.test"); body.put("phone", phone);
        body.put("practitionerId", "synthetic-doctor"); body.put("serviceType", "acupuncture");
        body.put("startTime", "2026-11-10 09:00:00"); body.put("endTime", "2026-11-10 09:30:00");
        body.put("notes", "Synthetic optional legacy intake summary");
        return body;
    }
}
