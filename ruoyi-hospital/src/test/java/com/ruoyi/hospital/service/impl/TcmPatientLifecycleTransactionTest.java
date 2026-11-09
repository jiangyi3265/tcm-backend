package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.mapper.*;
import com.ruoyi.hospital.service.*;

/** Real Spring transactions and H2 row locks: lifecycle lock must survive through commit.
 * Uses time blocks to isolate appointment persistence from unrelated scheduling rules. */
class TcmPatientLifecycleTransactionTest {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private ITcmAppointmentService booking;
    private ITcmConsultationService clinical;
    private final CountDownLatch parentLockAttempt = new CountDownLatch(1);
    private final CountDownLatch atCommit = new CountDownLatch(1), releaseCommit = new CountDownLatch(1);
    private final AtomicBoolean gateChildCommit = new AtomicBoolean();
    private final ThreadLocal<Boolean> childWriter = ThreadLocal.withInitial(() -> false);

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE patients (id VARCHAR(64) PRIMARY KEY, body CLOB)");
        jdbc.execute("CREATE TABLE appointments (id VARCHAR(64) PRIMARY KEY, body CLOB)");
        jdbc.execute("CREATE TABLE consultations (id VARCHAR(64) PRIMARY KEY, body CLOB)");
        for (String id : List.of("old", "target")) jdbc.update("INSERT INTO patients VALUES (?,?)", id,
                JSON.toJSONString(TcmPatientLifecycleWriteTest.patient(id, "active")));
        manager = new DataSourceTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status) {
                if (childWriter.get() && gateChildCommit.compareAndSet(true, false)) {
                    atCommit.countDown(); await(releaseCommit);
                }
                super.doCommit(status);
            }
        };
        TcmPatientMapper patients = mock(TcmPatientMapper.class);
        when(patients.selectTcmPatientByIdForUpdate(anyString())).thenAnswer(call -> {
            if (childWriter.get() && "target".equals(call.getArgument(0))) parentLockAttempt.countDown();
            return row("patients", call.getArgument(0), TcmPatient.class, true);
        });
        TcmAppointmentMapper appointments = mock(TcmAppointmentMapper.class);
        when(appointments.selectTcmAppointmentById(anyString())).thenAnswer(c -> row("appointments", c.getArgument(0), TcmAppointment.class, false));
        when(appointments.selectTcmAppointmentForUpdate(anyString())).thenAnswer(c -> row("appointments", c.getArgument(0), TcmAppointment.class, true));
        when(appointments.insertTcmAppointment(any())).thenAnswer(c -> insert("appointments", ((TcmAppointment)c.getArgument(0)).getId(), c.getArgument(0)));
        when(appointments.updateTcmAppointment(any())).thenAnswer(c -> update("appointments", ((TcmAppointment)c.getArgument(0)).getId(), c.getArgument(0)));
        TcmAppointmentServiceImpl appointmentTarget = new TcmAppointmentServiceImpl();
        ReflectionTestUtils.setField(appointmentTarget, "appointmentMapper", appointments);
        ReflectionTestUtils.setField(appointmentTarget, "patientMapper", patients);
        booking = proxy(appointmentTarget, ITcmAppointmentService.class);
        TcmConsultationMapper consultations = mock(TcmConsultationMapper.class);
        when(consultations.selectTcmConsultationById(anyString())).thenAnswer(c -> row("consultations", c.getArgument(0), TcmConsultation.class, false));
        when(consultations.selectTcmConsultationForUpdate(anyString())).thenAnswer(c -> row("consultations", c.getArgument(0), TcmConsultation.class, true));
        when(consultations.insertTcmConsultation(any())).thenAnswer(c -> insert("consultations", ((TcmConsultation)c.getArgument(0)).getId(), c.getArgument(0)));
        when(consultations.updateTcmConsultation(any())).thenAnswer(c -> update("consultations", ((TcmConsultation)c.getArgument(0)).getId(), c.getArgument(0)));
        TcmConsultationServiceImpl consultationTarget = new TcmConsultationServiceImpl();
        ReflectionTestUtils.setField(consultationTarget, "consultationMapper", consultations);
        ReflectionTestUtils.setField(consultationTarget, "patientMapper", patients);
        ReflectionTestUtils.setField(consultationTarget, "patientFileService", mock(ITcmPatientFileService.class));
        clinical = proxy(consultationTarget, ITcmConsultationService.class);
    }
    @ParameterizedTest @ValueSource(strings={"appointment-create", "appointment-retarget", "consultation-create", "consultation-retarget"})
    void retirementCommittingBeforeWriteMakesWaitingWriteReject(String operation) throws Exception {
        seedExisting(operation);
        CountDownLatch retiredLocked = new CountDownLatch(1), releaseRetirement = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> retirement = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                row("patients", "target", TcmPatient.class, true); retiredLocked.countDown(); await(releaseRetirement);
                update("patients", "target", TcmPatientLifecycleWriteTest.patient("target", "merged")); return null;
            }));
            assertTrue(retiredLocked.await(3, TimeUnit.SECONDS));
            Future<?> write = pool.submit(() -> write(operation));
            assertTrue(parentLockAttempt.await(3, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> write.get(150, TimeUnit.MILLISECONDS));
            releaseRetirement.countDown(); retirement.get(3, TimeUnit.SECONDS);
            ExecutionException error = assertThrows(ExecutionException.class, () -> write.get(3, TimeUnit.SECONDS));
            assertTrue(error.getCause() instanceof ServiceException);
            String table = operation.startsWith("appointment") ? "appointments" : "consultations";
            assertEquals(operation.endsWith("retarget") ? 1 : 0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            if (operation.endsWith("retarget")) assertTrue(jdbc.queryForObject("SELECT body FROM " + table, String.class).contains("old"));
        } finally { releaseRetirement.countDown(); releaseCommit.countDown(); pool.shutdownNow(); }
    }
    @ParameterizedTest @ValueSource(strings={"appointment-create", "appointment-retarget", "consultation-create", "consultation-retarget"})
    void writeKeepsPatientLockUntilItsTransactionCommit(String operation) throws Exception {
        seedExisting(operation); gateChildCommit.set(true);
        CountDownLatch retirementAttempt = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> write = pool.submit(() -> write(operation));
            assertTrue(atCommit.await(3, TimeUnit.SECONDS));
            Future<Integer> retirement = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                retirementAttempt.countDown(); row("patients", "target", TcmPatient.class, true);
                String table = operation.startsWith("appointment") ? "appointments" : "consultations";
                int committedChildren = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
                update("patients", "target", TcmPatientLifecycleWriteTest.patient("target", "merged"));
                return committedChildren;
            }));
            assertTrue(retirementAttempt.await(3, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> retirement.get(150, TimeUnit.MILLISECONDS));
            releaseCommit.countDown(); write.get(3, TimeUnit.SECONDS);
            assertEquals(1, retirement.get(3, TimeUnit.SECONDS));
        } finally { releaseCommit.countDown(); pool.shutdownNow(); }
    }
    private void seedExisting(String operation) {
        if (!operation.endsWith("retarget")) return;
        if (operation.startsWith("appointment")) insert("appointments", "a", TcmPatientLifecycleWriteTest.appointment("old"));
        else insert("consultations", "c", TcmPatientLifecycleWriteTest.consultation("old"));
    }
    private void write(String operation) {
        childWriter.set(true);
        try {
            if (operation.startsWith("appointment")) {
                TcmAppointment a = TcmPatientLifecycleWriteTest.appointment("target");
                if (operation.endsWith("retarget")) booking.updateTcmAppointment(a); else booking.insertTcmAppointment(a);
            } else {
                TcmConsultation c = TcmPatientLifecycleWriteTest.consultation("target");
                if (operation.endsWith("retarget")) clinical.updateTcmConsultation(c, "actor"); else clinical.insertTcmConsultation(c);
            }
        } finally { childWriter.remove(); }
    }
    private <T> T proxy(Object target, Class<T> type) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
    }
    private <T> T row(String table, String id, Class<T> type, boolean lock) {
        List<T> values = jdbc.query("SELECT body FROM " + table + " WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, index) -> JSON.parseObject(rs.getString(1), type), id);
        return values.isEmpty() ? null : values.get(0);
    }
    private int insert(String table, String id, Object value) { return jdbc.update("INSERT INTO " + table + " VALUES (?,?)", id, JSON.toJSONString(value)); }
    private int update(String table, String id, Object value) { return jdbc.update("UPDATE " + table + " SET body = ? WHERE id = ?", JSON.toJSONString(value), id); }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Synthetic latch timed out"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
    }
}

