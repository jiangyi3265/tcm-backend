package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.mapper.*;
import com.ruoyi.hospital.service.*;

/** Both operations execute real service code and JDBC row locks in separate transactions. */
class TcmFileDeletionLockOrderTest {
    @Test void deletingAFileAndEditingItsConsultationCanBothCommitWithoutOppositeParentChildLocks() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:file_lock_order_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2500");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE patients (id VARCHAR(64) PRIMARY KEY, payload VARCHAR(10000))");
        jdbc.execute("CREATE TABLE consultations (id VARCHAR(64) PRIMARY KEY, patient_id VARCHAR(64), payload VARCHAR(10000))");
        jdbc.execute("CREATE TABLE files (id BIGINT PRIMARY KEY, deleted INT)");
        String document = "{\"documents\":[{\"filePath\":\"hospital-private/synthetic/document.png\",\"name\":\"fixture\"}]}";
        jdbc.update("INSERT INTO patients VALUES ('p', ?)", document);
        jdbc.update("INSERT INTO consultations VALUES ('c', 'p', ?)", document);
        jdbc.update("INSERT INTO files VALUES (1,0)");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        CountDownLatch deletionHasChild = new CountDownLatch(1), editAttemptedParent = new CountDownLatch(1), editHasParent = new CountDownLatch(1);
        ThreadLocal<Boolean> editing = ThreadLocal.withInitial(() -> false);
        TcmPatientMapper patients = mock(TcmPatientMapper.class);
        when(patients.selectTcmPatientById(anyString())).thenAnswer(call -> patient(jdbc, false));
        when(patients.selectTcmPatientByIdForUpdate(anyString())).thenAnswer(call -> {
            if (editing.get()) editAttemptedParent.countDown();
            TcmPatient patient = patient(jdbc, true);
            if (editing.get()) editHasParent.countDown();
            return patient;
        });
        when(patients.updateFilePayloadIfUnchanged(any(), any())).thenAnswer(call -> {
            TcmPatient update = call.getArgument(0);
            return jdbc.update("UPDATE patients SET payload=? WHERE id=? AND payload=?", update.getPayload(), update.getId(), (String)call.getArgument(1));
        });
        TcmConsultationMapper consultations = mock(TcmConsultationMapper.class);
        when(consultations.selectTcmConsultationById(anyString())).thenAnswer(call -> consultation(jdbc, false));
        when(consultations.selectTcmConsultationForUpdate(anyString())).thenAnswer(call -> consultation(jdbc, true));
        when(consultations.selectTcmConsultationList(any())).thenAnswer(call -> List.of(consultation(jdbc, false)));
        when(consultations.selectTcmConsultationsByPatientIdForUpdate("p")).thenAnswer(call -> List.of(consultation(jdbc, true)));
        when(consultations.updatePdfPayloadIfUnchanged(any(), any())).thenAnswer(call -> {
            TcmConsultation update = call.getArgument(0);
            int result = jdbc.update("UPDATE consultations SET payload=? WHERE id=? AND payload=?", update.getPayload(), update.getId(), (String)call.getArgument(1));
            deletionHasChild.countDown();
            assertTrue(editAttemptedParent.await(3, TimeUnit.SECONDS));
            // If deletion owns the parent already, editor correctly waits here. Otherwise
            // the editor acquires it, exposing the parent/child lock inversion.
            editHasParent.await(300, TimeUnit.MILLISECONDS);
            return result;
        });
        when(consultations.updateTcmConsultation(any())).thenAnswer(call -> {
            TcmConsultation update = call.getArgument(0);
            return jdbc.update("UPDATE consultations SET payload=? WHERE id=?", update.getPayload(), update.getId());
        });
        TcmPatientFileMapper fileMapper = mock(TcmPatientFileMapper.class);
        when(fileMapper.selectTcmPatientFileById(1L)).thenAnswer(call -> file(jdbc));
        when(fileMapper.selectTcmPatientFileByIdForUpdate(1L)).thenAnswer(call -> {
            jdbc.queryForObject("SELECT id FROM files WHERE id=1 FOR UPDATE", Long.class);
            return file(jdbc);
        });
        when(fileMapper.markTcmPatientFileDeleted(1L)).thenAnswer(call -> jdbc.update("UPDATE files SET deleted=1 WHERE id=1"));
        when(fileMapper.selectDeletedFilesByPatientId("p")).thenAnswer(call -> file(jdbc).isDeleted() ? List.of(file(jdbc)) : Collections.emptyList());
        when(fileMapper.selectDeletedFilesByPatientIdForUpdate("p")).thenAnswer(call -> {
            List<Long> deleted = jdbc.queryForList("SELECT id FROM files WHERE deleted=1 ORDER BY id FOR UPDATE", Long.class);
            return deleted.isEmpty() ? Collections.emptyList() : List.of(file(jdbc));
        });
        TcmPatientFileServiceImpl fileTarget = new TcmPatientFileServiceImpl();
        ReflectionTestUtils.setField(fileTarget, "tcmPatientFileMapper", fileMapper);
        ReflectionTestUtils.setField(fileTarget, "consultationMapper", consultations);
        ReflectionTestUtils.setField(fileTarget, "patientMapper", patients);
        ITcmPatientFileService files = proxy(fileTarget, manager, ITcmPatientFileService.class);
        TcmConsultationServiceImpl clinicalTarget = new TcmConsultationServiceImpl();
        ReflectionTestUtils.setField(clinicalTarget, "patientMapper", patients);
        ReflectionTestUtils.setField(clinicalTarget, "consultationMapper", consultations);
        ReflectionTestUtils.setField(clinicalTarget, "patientFileService", files);
        ITcmConsultationService clinical = proxy(clinicalTarget, manager, ITcmConsultationService.class);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> deleting = workers.submit(() -> files.deleteTcmPatientFileById(1L));
            assertTrue(deletionHasChild.await(3, TimeUnit.SECONDS));
            Future<Integer> edit = workers.submit(() -> {
                editing.set(true);
                try {
                    TcmConsultation incoming = consultation(jdbc, false);
                    incoming.setPayload("{\"chiefComplaint\":\"Synthetic edited note\"}");
                    return clinical.updateTcmConsultation(incoming, "actor");
                } finally { editing.remove(); }
            });
            assertEquals(1, deleting.get(5, TimeUnit.SECONDS));
            assertEquals(1, edit.get(5, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT deleted FROM files", Integer.class));
            assertTrue(jdbc.queryForObject("SELECT payload FROM consultations", String.class).contains("Synthetic edited note"));
            assertTrue(JSONObject.parseObject(jdbc.queryForObject("SELECT payload FROM patients", String.class)).getJSONArray("documents").isEmpty());
        } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    private static TcmPatient patient(JdbcTemplate jdbc, boolean lock) {
        TcmPatient patient = new TcmPatient(); patient.setId("p"); patient.setIsActive(1);
        patient.setPayload(jdbc.queryForObject("SELECT payload FROM patients WHERE id='p'" + (lock ? " FOR UPDATE" : ""), String.class));
        return patient;
    }
    private static TcmConsultation consultation(JdbcTemplate jdbc, boolean lock) {
        TcmConsultation result = new TcmConsultation(); result.setId("c"); result.setConsultationId("C-1"); result.setPatientId("p");
        result.setStatus("draft"); result.setVersion(1);
        result.setPayload(jdbc.queryForObject("SELECT payload FROM consultations WHERE id='c'" + (lock ? " FOR UPDATE" : ""), String.class));
        return result;
    }
    private static TcmPatientFile file(JdbcTemplate jdbc) {
        TcmPatientFile file = new TcmPatientFile(); file.setId(1L); file.setPatientId("p"); file.setConsultationId("c");
        file.setFilePath("hospital-private/synthetic/document.png");
        file.setFileType(jdbc.queryForObject("SELECT deleted FROM files WHERE id=1", Integer.class) == 1 ? "__deleted__" : "document");
        return file;
    }
    private static <T> T proxy(Object target, DataSourceTransactionManager manager, Class<T> type) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
    }
}
