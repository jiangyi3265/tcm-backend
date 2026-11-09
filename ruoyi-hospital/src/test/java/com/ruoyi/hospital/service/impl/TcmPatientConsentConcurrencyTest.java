package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.aop.framework.ProxyFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmPatientFileService;
import com.ruoyi.hospital.service.ITcmPdfService;
import com.ruoyi.hospital.util.ConsentDocumentTemplate;

/** Real mapper SQL and independently committed requests; all data and PDF calls are synthetic. */
class TcmPatientConsentConcurrencyTest
{
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private TcmPatientMapper actualMapper;
    private TcmPatientMapper gatedMapper;
    private TcmPatientServiceImpl service;
    private ITcmPdfService pdf;
    private TcmConsultationMapper consultations;
    private ExecutorService workers;
    private final AtomicBoolean pauseCommit = new AtomicBoolean();
    private final CountDownLatch committing = new CountDownLatch(1), allowCommit = new CountDownLatch(1);

    @BeforeEach void setup() throws Exception
    {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:consent_race_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE tcm_patient (id VARCHAR(64) PRIMARY KEY, name VARCHAR(128), first_name VARCHAR(128),"
                + "last_name VARCHAR(128), email VARCHAR(128), phone VARCHAR(64), practitioner_id VARCHAR(64),"
                + "is_active INT, consent_signed INT, consent_signed_at VARCHAR(32), consent_token VARCHAR(64),"
                + "consent_token_expires VARCHAR(32), merged_into VARCHAR(64), deleted_at VARCHAR(32),"
                + "payload VARCHAR(50000), create_time TIMESTAMP, update_time TIMESTAMP)");
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source) {
            @Override protected void doCommit(DefaultTransactionStatus status)
            {
                if (pauseCommit.compareAndSet(true, false))
                {
                    committing.countDown();
                    try { await(allowCommit); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                }
                super.doCommit(status);
            }
        };
        transaction = new TransactionTemplate(manager);
        Configuration configuration = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), source));
        configuration.getTypeAliasRegistry().registerAlias("TcmPatient", TcmPatient.class);
        String resource = "mapper/hospital/TcmPatientMapper.xml";
        String xml;
        try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resource))
        {
            assertNotNull(stream);
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        new XMLMapperBuilder(new StringReader(xml), configuration, resource, configuration.getSqlFragments()).parse();
        actualMapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(TcmPatientMapper.class);
        gatedMapper = mock(TcmPatientMapper.class, delegatesTo(actualMapper));
        service = new TcmPatientServiceImpl();
        ReflectionTestUtils.setField(service, "tcmPatientMapper", gatedMapper);
        consultations = mock(TcmConsultationMapper.class);
        ReflectionTestUtils.setField(service, "tcmConsultationMapper", consultations);
        ReflectionTestUtils.setField(service, "tcmAppointmentMapper", mock(TcmAppointmentMapper.class));
        ReflectionTestUtils.setField(service, "patientFileService", mock(ITcmPatientFileService.class));
        pdf = mock(ITcmPdfService.class);
        when(pdf.generateConsentForm(anyString(), anyString())).thenReturn(Map.of(
                "filePath", "hospital-private/fixture/consent.pdf", "url", "https://test.invalid/consent.pdf"));
        ReflectionTestUtils.setField(service, "pdfService", pdf);
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (TcmPatientServiceImpl) factory.getProxy();
        workers = Executors.newFixedThreadPool(2);
        jdbc.update("INSERT INTO tcm_patient(id,name,is_active,consent_signed,consent_token,consent_token_expires,payload)"
                + " VALUES (?,?,1,0,?,'2099-01-01 00:00:00','{}')", "source", "Synthetic Patient", "source-token");
        jdbc.update("INSERT INTO tcm_patient(id,name,is_active,consent_signed,payload) VALUES (?,?,1,0,'{}')",
                "keep", "Synthetic Patient");
    }

    @AfterEach void cleanup() throws Exception
    {
        allowCommit.countDown();
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test void staleSigningReadCannotReactivateAProfileMergedBeforeSignatureWrite() throws Exception
    {
        CountDownLatch read = new CountDownLatch(1), proceed = new CountDownLatch(1);
        pauseTokenReads(read, proceed);
        Future<Throwable> signing = workers.submit(() -> sign("First Signature"));
        try
        {
            await(read);
            transaction.executeWithoutResult(status -> service.mergeTcmPatients("keep", "source"));
            assertNotNull(actualMapper.selectTcmPatientById("source").getDeletedAt());
        }
        finally { proceed.countDown(); }

        Throwable failure = signing.get(5, TimeUnit.SECONDS);
        TcmPatient retired = actualMapper.selectTcmPatientById("source");
        assertAll(
                () -> assertTrue(failure instanceof ServiceException, "The stale token must be rejected"),
                () -> assertNotNull(retired.getDeletedAt(), "A stale sign request must not clear the merge's deleted marker"),
                () -> assertEquals(0, retired.getIsActive()),
                () -> assertEquals(0, retired.getConsentSigned()));
        verify(pdf, never()).generateConsentForm(anyString(), anyString());
    }

    @Test void twoRequestsReadingTheSameConsentTokenCanOnlySignOnce() throws Exception
    {
        CountDownLatch reads = new CountDownLatch(2), proceed = new CountDownLatch(1);
        pauseTokenReads(reads, proceed);
        Future<Throwable> first = workers.submit(() -> sign("First Signature"));
        Future<Throwable> second = workers.submit(() -> sign("Second Signature"));
        try { await(reads); }
        finally { proceed.countDown(); }

        Throwable firstFailure = first.get(5, TimeUnit.SECONDS);
        Throwable secondFailure = second.get(5, TimeUnit.SECONDS);
        int successful = (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0);
        assertEquals(1, successful, "Only one request may consume a consent token");
        assertTrue((firstFailure != null ? firstFailure : secondFailure) instanceof ServiceException);
        verify(pdf, times(1)).generateConsentForm(eq("source"), anyString());
        assertNull(actualMapper.selectTcmPatientByConsentToken("source-token"));
    }

    @Test void delayedConsentPdfMetadataCannotReactivateProfileMergedAfterSignature() throws Exception
    {
        CountDownLatch pdfStarted = new CountDownLatch(1), finishPdf = new CountDownLatch(1);
        when(pdf.generateConsentForm(anyString(), anyString())).thenAnswer(call -> {
            pdfStarted.countDown(); await(finishPdf);
            return Map.of("filePath", "hospital-private/fixture/late-consent.pdf", "url", "https://test.invalid/late.pdf");
        });
        Future<Throwable> signing = workers.submit(() -> sign("First Signature"));
        Future<Throwable> merging = null;
        try
        {
            await(pdfStarted);
            assertEquals(0, actualMapper.selectTcmPatientById("source").getConsentSigned(),
                    "Signature and PDF metadata are not committed while PDF generation is pending");
            CountDownLatch lockingSource = observeNextSourceLock();
            merging = workers.submit(() -> merge("keep", "source"));
            await(lockingSource);
            Future<Throwable> pendingMerge = merging;
            assertThrows(TimeoutException.class, () -> pendingMerge.get(100, TimeUnit.MILLISECONDS),
                    "Merge must wait for the signature's PDF and transaction");
        }
        finally { finishPdf.countDown(); }
        assertNull(signing.get(5, TimeUnit.SECONDS));
        assertNotNull(merging);
        assertNull(merging.get(5, TimeUnit.SECONDS));
        TcmPatient retired = actualMapper.selectTcmPatientById("source");
        TcmPatient kept = actualMapper.selectTcmPatientById("keep");
        assertAll(
                () -> assertNotNull(retired.getDeletedAt(), "PDF metadata must not restore a retired patient's old profile snapshot"),
                () -> assertEquals(0, retired.getIsActive()),
                () -> assertEquals("keep", retired.getMergedInto()),
                () -> assertEquals(1, kept.getConsentSigned()),
                () -> assertEquals("First Signature", JSONObject.parseObject(kept.getPayload()).getString("consentSignatureName")));
    }

    @Test void signatureRowLockIsHeldThroughTheActualCommit() throws Exception
    {
        pauseCommit.set(true);
        Future<Throwable> first = workers.submit(() -> sign("First Signature"));
        Future<Throwable> second = null;
        try
        {
            await(committing);
            assertEquals(0, actualMapper.selectTcmPatientById("source").getConsentSigned());
            CountDownLatch lockingSource = observeNextSourceLock();
            second = workers.submit(() -> sign("Second Signature"));
            await(lockingSource);
            Future<Throwable> pendingSignature = second;
            assertThrows(TimeoutException.class, () -> pendingSignature.get(100, TimeUnit.MILLISECONDS));
        }
        finally { allowCommit.countDown(); }
        assertNull(first.get(5, TimeUnit.SECONDS));
        assertNotNull(second);
        assertTrue(second.get(5, TimeUnit.SECONDS) instanceof ServiceException);
        verify(pdf, times(1)).generateConsentForm(eq("source"), anyString());
        assertEquals("First Signature", JSONObject.parseObject(actualMapper.selectTcmPatientById("source").getPayload())
                .getString("consentSignatureName"));
    }

    @Test void reverseAndRepeatedMergeCannotFormARetiredProfileCycle()
    {
        service.mergeTcmPatients("keep", "source");
        assertThrows(ServiceException.class, () -> service.mergeTcmPatients("source", "keep"));
        assertThrows(ServiceException.class, () -> service.mergeTcmPatients("keep", "source"));
        TcmPatient kept = actualMapper.selectTcmPatientById("keep");
        TcmPatient retired = actualMapper.selectTcmPatientById("source");
        assertEquals(1, kept.getIsActive());
        assertNull(kept.getMergedInto());
        assertEquals(0, retired.getIsActive());
        assertEquals("keep", retired.getMergedInto());
    }

    @Test void oppositeConcurrentMergesLockProfilesInTheSameOrder() throws Exception
    {
        CountDownLatch ready = new CountDownLatch(2), proceed = new CountDownLatch(1);
        doAnswer(call -> {
            ready.countDown(); await(proceed);
            return actualMapper.selectTcmPatientByIdForUpdate("keep");
        }).when(gatedMapper).selectTcmPatientByIdForUpdate("keep");
        Future<Throwable> first = workers.submit(() -> merge("keep", "source"));
        Future<Throwable> second = workers.submit(() -> merge("source", "keep"));
        try { await(ready); }
        finally { proceed.countDown(); }
        Throwable firstFailure = first.get(5, TimeUnit.SECONDS), secondFailure = second.get(5, TimeUnit.SECONDS);
        assertEquals(1, (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0));
        assertTrue((firstFailure != null ? firstFailure : secondFailure) instanceof ServiceException);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tcm_patient WHERE is_active=1 AND merged_into IS NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM tcm_patient WHERE is_active=0 AND merged_into IS NOT NULL", Integer.class));
    }

    @Test void inactiveOrDeletedProfilesCannotBeMerged()
    {
        jdbc.update("UPDATE tcm_patient SET is_active=0 WHERE id='source'");
        assertThrows(ServiceException.class, () -> service.mergeTcmPatients("keep", "source"));
        jdbc.update("UPDATE tcm_patient SET is_active=1,deleted_at='2026-01-01 00:00:00' WHERE id='source'");
        assertThrows(ServiceException.class, () -> service.mergeTcmPatients("keep", "source"));
        assertNull(actualMapper.selectTcmPatientById("source").getMergedInto());
        assertNull(actualMapper.selectTcmPatientById("keep").getConsentToken());
    }

    @Test void pdfFailureStillCommitsOnlyOneSignature()
    {
        when(pdf.generateConsentForm(anyString(), anyString())).thenThrow(new IllegalStateException("synthetic PDF failure"));
        assertNull(sign("First Signature"));
        assertTrue(sign("Second Signature") instanceof ServiceException);
        assertEquals(1, actualMapper.selectTcmPatientById("source").getConsentSigned());
        assertNull(actualMapper.selectTcmPatientByConsentToken("source-token"));
        verify(pdf, times(1)).generateConsentForm(eq("source"), anyString());
    }

    @Test void consentTokenGenerationCannotUndoASignatureWhileItsCommitIsPending() throws Exception
    {
        pauseCommit.set(true);
        Future<Throwable> signing = workers.submit(() -> sign("First Signature"));
        Future<String> generating = null;
        try
        {
            await(committing);
            CountDownLatch lockingSource = observeNextSourceLock();
            generating = workers.submit(() -> service.generateConsentToken("source"));
            await(lockingSource);
            Future<String> pendingGeneration = generating;
            assertThrows(TimeoutException.class, () -> pendingGeneration.get(100, TimeUnit.MILLISECONDS));
        }
        finally { allowCommit.countDown(); }
        assertNull(signing.get(5, TimeUnit.SECONDS));
        assertNotNull(generating);
        Future<String> completedGeneration = generating;
        assertTrue(assertThrows(java.util.concurrent.ExecutionException.class,
                () -> completedGeneration.get(5, TimeUnit.SECONDS)).getCause() instanceof ServiceException);
        assertEquals(1, actualMapper.selectTcmPatientById("source").getConsentSigned());
        assertNull(actualMapper.selectTcmPatientById("source").getConsentToken());
    }

    @Test void mergedProfilesCannotBeRestoredOrReceiveANewConsentToken()
    {
        service.mergeTcmPatients("keep", "source");
        assertThrows(ServiceException.class, () -> service.restoreTcmPatient("source"));
        assertThrows(ServiceException.class, () -> service.generateConsentToken("source"));
        assertNotNull(actualMapper.selectTcmPatientById("source").getDeletedAt());
        assertEquals("keep", actualMapper.selectTcmPatientById("source").getMergedInto());
    }

    @Test void softDeleteWaitsForNewConsultationCommitAndThenRefusesDeletion() throws Exception
    {
        jdbc.execute("CREATE TABLE fixture_consultation (id VARCHAR(64), patient_id VARCHAR(64))");
        when(consultations.selectTcmConsultationList(any())).thenAnswer(call -> jdbc.query(
                "SELECT id,patient_id FROM fixture_consultation", (row, index) -> {
                    TcmConsultation child = new TcmConsultation();
                    child.setId(row.getString("id")); child.setPatientId(row.getString("patient_id")); return child;
                }));
        pauseCommit.set(true);
        Future<?> inserting = workers.submit(() -> transaction.executeWithoutResult(status -> {
            actualMapper.selectTcmPatientByIdForUpdate("source");
            jdbc.update("INSERT INTO fixture_consultation VALUES ('new-consultation','source')");
        }));
        Future<TcmPatient> deleting = null;
        try
        {
            await(committing);
            CountDownLatch lockingSource = observeNextSourceLock();
            deleting = workers.submit(() -> service.softDeleteTcmPatient("source"));
            await(lockingSource);
            Future<TcmPatient> pendingDeletion = deleting;
            assertThrows(TimeoutException.class, () -> pendingDeletion.get(100, TimeUnit.MILLISECONDS));
        }
        finally { allowCommit.countDown(); }
        inserting.get(5, TimeUnit.SECONDS);
        assertNotNull(deleting);
        Future<TcmPatient> completedDeletion = deleting;
        assertTrue(assertThrows(java.util.concurrent.ExecutionException.class,
                () -> completedDeletion.get(5, TimeUnit.SECONDS)).getCause() instanceof ServiceException);
        assertNull(actualMapper.selectTcmPatientById("source").getDeletedAt());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fixture_consultation", Integer.class));
    }

    @Test void hardDeleteWaitsForRestoreCommitAndCannotDeleteTheRestoredProfile() throws Exception
    {
        jdbc.update("UPDATE tcm_patient SET deleted_at='2026-01-01 00:00:00' WHERE id='source'");
        pauseCommit.set(true);
        Future<TcmPatient> restoring = workers.submit(() -> service.restoreTcmPatient("source"));
        Future<Integer> deleting = null;
        try
        {
            await(committing);
            CountDownLatch lockingSource = observeNextSourceLock();
            deleting = workers.submit(() -> service.hardDeleteTcmPatient("source"));
            await(lockingSource);
            Future<Integer> pendingDeletion = deleting;
            assertThrows(TimeoutException.class, () -> pendingDeletion.get(100, TimeUnit.MILLISECONDS));
        }
        finally { allowCommit.countDown(); }
        assertNotNull(restoring.get(5, TimeUnit.SECONDS));
        assertNotNull(deleting);
        Future<Integer> completedDeletion = deleting;
        assertTrue(assertThrows(java.util.concurrent.ExecutionException.class,
                () -> completedDeletion.get(5, TimeUnit.SECONDS)).getCause() instanceof ServiceException);
        assertNull(actualMapper.selectTcmPatientById("source").getDeletedAt());
    }

    @Test void legacyBookingSummaryUsesCurrentProfileAfterEarlierCachedRead()
    {
        transaction.executeWithoutResult(status -> {
            assertEquals(0, actualMapper.selectTcmPatientById("source").getConsentSigned());
            try { assertNull(workers.submit(() -> sign("First Signature")).get(5, TimeUnit.SECONDS)); }
            catch (Exception error) { throw new IllegalStateException(error); }
            service.savePublicBookingIntakeSummary("source", "fixture-appointment", Map.of("chiefComplaint", "Fixture summary"));
        });
        TcmPatient current = actualMapper.selectTcmPatientById("source");
        assertEquals(1, current.getConsentSigned());
        assertNull(current.getConsentToken());
        JSONObject payload = JSONObject.parseObject(current.getPayload());
        assertEquals("First Signature", payload.getString("consentSignatureName"));
        assertEquals("Fixture summary", payload.getJSONObject("latestIntakeFormData").getString("chiefComplaint"));
    }

    @Test void legacyPatientIntakeCannotOverwriteAConcurrentSignature() throws Exception
    {
        installPatientIntakeToken();
        CountDownLatch read = new CountDownLatch(1), proceed = new CountDownLatch(1);
        pauseIntakeTokenReads(read, proceed);
        Future<Throwable> submitting = workers.submit(() -> submitPatientIntake());
        try
        {
            await(read);
            assertNull(sign("Concurrent Signature"));
        }
        finally { proceed.countDown(); }
        assertNull(submitting.get(5, TimeUnit.SECONDS));
        TcmPatient current = actualMapper.selectTcmPatientById("source");
        JSONObject payload = JSONObject.parseObject(current.getPayload());
        assertAll(
                () -> assertEquals(1, current.getConsentSigned()),
                () -> assertNull(current.getConsentToken()),
                () -> assertEquals("Concurrent Signature", payload.getString("consentSignatureName")),
                () -> assertEquals("hospital-private/fixture/consent.pdf", payload.getString("consentPdfPath")),
                () -> assertEquals("Fixture intake", payload.getJSONObject("latestIntakeFormData").getString("chiefComplaint")));
    }

    @Test void legacyPatientIntakeCannotRestoreAProfileMergedAfterItsTokenLookup() throws Exception
    {
        installPatientIntakeToken();
        CountDownLatch read = new CountDownLatch(1), proceed = new CountDownLatch(1);
        pauseIntakeTokenReads(read, proceed);
        Future<Throwable> submitting = workers.submit(() -> submitPatientIntake());
        try
        {
            await(read);
            service.mergeTcmPatients("keep", "source");
        }
        finally { proceed.countDown(); }
        Throwable failure = submitting.get(5, TimeUnit.SECONDS);
        TcmPatient retired = actualMapper.selectTcmPatientById("source");
        assertAll(
                () -> assertTrue(failure instanceof ServiceException, "A stale source token must be rejected"),
                () -> assertNotNull(retired.getDeletedAt()),
                () -> assertEquals(0, retired.getIsActive()));
    }

    @Test void intakeTokenGenerationPreservesSignatureAfterAnEarlierCachedRead()
    {
        transaction.executeWithoutResult(status -> {
            assertEquals(0, actualMapper.selectTcmPatientById("source").getConsentSigned());
            try { assertNull(workers.submit(() -> sign("Concurrent Signature")).get(5, TimeUnit.SECONDS)); }
            catch (Exception error) { throw new IllegalStateException(error); }
            assertNotNull(service.generateIntakeToken("source"));
        });
        TcmPatient current = actualMapper.selectTcmPatientById("source");
        JSONObject payload = JSONObject.parseObject(current.getPayload());
        assertAll(
                () -> assertEquals(1, current.getConsentSigned()),
                () -> assertNull(current.getConsentToken()),
                () -> assertEquals("Concurrent Signature", payload.getString("consentSignatureName")),
                () -> assertNotNull(payload.getString("intakeToken")));
    }

    @Test void mergeTransfersPatientIntakeTokenAndExpiryTogetherAndRevokesTheSource()
    {
        installPatientIntakeToken();
        jdbc.update("UPDATE tcm_patient SET payload=? WHERE id='keep'", "{\"intakeTokenExpires\":\"2000-01-01 00:00:00\"}");
        service.mergeTcmPatients("keep", "source");
        JSONObject kept = JSONObject.parseObject(actualMapper.selectTcmPatientById("keep").getPayload());
        JSONObject retired = JSONObject.parseObject(actualMapper.selectTcmPatientById("source").getPayload());
        assertAll(
                () -> assertEquals("patient-intake", kept.getString("intakeToken")),
                () -> assertEquals("2099-01-01 00:00:00", kept.getString("intakeTokenExpires")),
                () -> assertFalse(retired.containsKey("intakeToken")),
                () -> assertFalse(retired.containsKey("intakeTokenExpires")));
    }

    @Test void twoPatientIntakeSubmissionsCanOnlyConsumeTheTokenOnce() throws Exception
    {
        installPatientIntakeToken();
        CountDownLatch reads = new CountDownLatch(2), proceed = new CountDownLatch(1);
        pauseIntakeTokenReads(reads, proceed);
        Future<Throwable> first = workers.submit(() -> submitPatientIntake());
        Future<Throwable> second = workers.submit(() -> submitPatientIntake());
        try { await(reads); }
        finally { proceed.countDown(); }
        Throwable firstFailure = first.get(5, TimeUnit.SECONDS), secondFailure = second.get(5, TimeUnit.SECONDS);
        assertEquals(1, (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0));
        assertTrue((firstFailure != null ? firstFailure : secondFailure) instanceof ServiceException);
        JSONObject payload = JSONObject.parseObject(actualMapper.selectTcmPatientById("source").getPayload());
        assertFalse(payload.containsKey("intakeToken"));
        assertTrue(payload.getBooleanValue("latestIntakeCompleted"));
    }

    @Test void intakeExpiryIsRecheckedOnTheLockedCurrentProfile() throws Exception
    {
        installPatientIntakeToken();
        CountDownLatch read = new CountDownLatch(1), proceed = new CountDownLatch(1);
        pauseIntakeTokenReads(read, proceed);
        Future<Throwable> submitting = workers.submit(() -> submitPatientIntake());
        try
        {
            await(read);
            jdbc.update("UPDATE tcm_patient SET payload=? WHERE id='source'",
                    "{\"intakeToken\":\"patient-intake\",\"intakeTokenExpires\":\"2000-01-01 00:00:00\"}");
        }
        finally { proceed.countDown(); }
        assertTrue(submitting.get(5, TimeUnit.SECONDS) instanceof ServiceException);
        JSONObject payload = JSONObject.parseObject(actualMapper.selectTcmPatientById("source").getPayload());
        assertEquals("patient-intake", payload.getString("intakeToken"));
        assertFalse(payload.getBooleanValue("latestIntakeCompleted"));
    }

    @Test void mergePreservesAnExistingIntakeTokenAndItsOwnMissingExpiry()
    {
        installPatientIntakeToken();
        jdbc.update("UPDATE tcm_patient SET payload=? WHERE id='keep'", "{\"intakeToken\":\"kept-intake\"}");
        service.mergeTcmPatients("keep", "source");
        JSONObject kept = JSONObject.parseObject(actualMapper.selectTcmPatientById("keep").getPayload());
        assertEquals("kept-intake", kept.getString("intakeToken"));
        assertFalse(kept.containsKey("intakeTokenExpires"));
        assertThrows(ServiceException.class, () -> service.selectByIntakeToken("patient-intake"));
        assertEquals("keep", service.selectByIntakeToken("kept-intake").getId());
    }

    @Test void legacyMergedProfileWithoutDeletedMarkerCannotExposeOrChangeAnIntake()
    {
        installPatientIntakeToken();
        jdbc.update("UPDATE tcm_patient SET merged_into='keep' WHERE id='source'");
        assertThrows(ServiceException.class, () -> service.selectByIntakeToken("patient-intake"));
        assertThrows(ServiceException.class, () -> service.generateIntakeToken("source"));
        assertThrows(ServiceException.class, () -> service.saveLatestIntakeForm("source", Map.of("chiefComplaint", "Must not save")));
        assertFalse(JSONObject.parseObject(actualMapper.selectTcmPatientById("source").getPayload())
                .getBooleanValue("latestIntakeCompleted"));
    }

    private void installPatientIntakeToken()
    {
        jdbc.update("UPDATE tcm_patient SET payload=? WHERE id='source'",
                "{\"intakeToken\":\"patient-intake\",\"intakeTokenExpires\":\"2099-01-01 00:00:00\"}");
    }

    private void pauseIntakeTokenReads(CountDownLatch read, CountDownLatch proceed)
    {
        doAnswer(call -> {
            java.util.List<TcmPatient> snapshot = actualMapper.selectTcmPatientList(call.getArgument(0));
            read.countDown(); await(proceed);
            return snapshot;
        }).when(gatedMapper).selectTcmPatientList(any());
    }

    private Throwable submitPatientIntake()
    {
        try
        {
            transaction.executeWithoutResult(status -> service.saveIntakeFormByToken("patient-intake", Map.of("chiefComplaint", "Fixture intake")));
            return null;
        }
        catch (Throwable failure) { return failure; }
    }

    private CountDownLatch observeNextSourceLock()
    {
        CountDownLatch locking = new CountDownLatch(1);
        doAnswer(call -> {
            locking.countDown();
            return actualMapper.selectTcmPatientByIdForUpdate("source");
        }).when(gatedMapper).selectTcmPatientByIdForUpdate("source");
        return locking;
    }

    private Throwable merge(String keep, String source)
    {
        try { service.mergeTcmPatients(keep, source); return null; }
        catch (Throwable failure) { return failure; }
    }

    private void pauseTokenReads(CountDownLatch read, CountDownLatch proceed)
    {
        doAnswer(call -> {
            TcmPatient snapshot = actualMapper.selectTcmPatientByConsentToken(call.getArgument(0));
            read.countDown(); await(proceed);
            return snapshot;
        }).when(gatedMapper).selectTcmPatientByConsentToken("source-token");
    }

    private Throwable sign(String signature)
    {
        Map<String, Object> acknowledgements = new LinkedHashMap<>();
        for (String section : ConsentDocumentTemplate.getSectionKeys()) acknowledgements.put(section, true);
        try { service.signConsentByToken("source-token", signature, acknowledgements); return null; }
        catch (Throwable failure) { return failure; }
    }

    private static void await(CountDownLatch latch) throws InterruptedException
    {
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Synthetic interleaving gate timed out");
    }
}
