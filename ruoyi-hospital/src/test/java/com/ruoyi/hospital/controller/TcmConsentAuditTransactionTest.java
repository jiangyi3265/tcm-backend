package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmConsultationModMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.*;
import com.ruoyi.hospital.service.impl.TcmAuditLogServiceImpl;
import com.ruoyi.hospital.service.impl.TcmConsultationModServiceImpl;
import com.ruoyi.hospital.service.impl.TcmPatientServiceImpl;
import com.ruoyi.hospital.util.ConsentDocumentTemplate;

/** Real controllers/services and Spring transaction proxies, with H2-backed mapper
 * substitutes. PDF generation is mocked: no patient files, SMTP, or external API. */
class TcmConsentAuditTransactionTest
{
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private TcmPatientController staff;
    private TcmConsentController publicConsent;
    private boolean failAudit;

    @BeforeEach void setup()
    {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:consent_audit_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE patients (id VARCHAR(64) PRIMARY KEY, body CLOB)");
        jdbc.execute("CREATE TABLE audit_events (patient_id VARCHAR(64))");
        TcmPatient initial = new TcmPatient(); initial.setId("patient"); initial.setName("Synthetic Patient");
        initial.setIsActive(1); initial.setConsentSigned(0); initial.setConsentToken("original-token");
        initial.setConsentTokenExpires("2099-12-31 12:00:00"); initial.setPayload("{\"notes\":\"Preserved clinical history\"}");
        jdbc.update("INSERT INTO patients VALUES (?, ?)", initial.getId(), JSON.toJSONString(initial));

        TcmPatientMapper mapper = mock(TcmPatientMapper.class);
        when(mapper.selectTcmPatientById("patient")).thenAnswer(call -> patient(false));
        when(mapper.selectTcmPatientByIdForUpdate("patient")).thenAnswer(call -> patient(true));
        when(mapper.selectTcmPatientByConsentToken(anyString())).thenAnswer(call -> {
            TcmPatient patient = patient(false);
            return call.getArgument(0).equals(patient.getConsentToken()) ? patient : null;
        });
        when(mapper.updateTcmPatient(any())).thenAnswer(call -> jdbc.update(
                "UPDATE patients SET body = ? WHERE id = 'patient'", JSON.toJSONString(call.getArgument(0))));
        ITcmPdfService pdf = mock(ITcmPdfService.class);
        when(pdf.generateConsentForm(anyString(), anyString())).thenReturn(Map.of(
                "filePath", "synthetic/signed.pdf", "url", "/synthetic/signed.pdf"));
        TcmPatientServiceImpl patientTarget = new TcmPatientServiceImpl();
        ReflectionTestUtils.setField(patientTarget, "tcmPatientMapper", mapper);
        ReflectionTestUtils.setField(patientTarget, "pdfService", pdf);
        ReflectionTestUtils.setField(patientTarget, "settingsService", mock(ITcmSettingsService.class));
        ITcmPatientService patients = proxy(patientTarget, ITcmPatientService.class);

        TcmConsultationModMapper auditMapper = mock(TcmConsultationModMapper.class);
        when(auditMapper.insertTcmConsultationMod(any())).thenAnswer(call -> {
            jdbc.update("INSERT INTO audit_events VALUES ('patient')");
            if (failAudit) throw new IllegalStateException("Synthetic audit failure");
            return 1;
        });
        TcmConsultationModServiceImpl mods = new TcmConsultationModServiceImpl();
        ReflectionTestUtils.setField(mods, "tcmConsultationModMapper", auditMapper);
        TcmAuditLogServiceImpl audit = new TcmAuditLogServiceImpl();
        ReflectionTestUtils.setField(audit, "consultationModService", mods);

        TcmPatientController staffTarget = new TcmPatientController();
        ReflectionTestUtils.setField(staffTarget, "patientService", patients);
        ReflectionTestUtils.setField(staffTarget, "auditLogService", audit);
        ReflectionTestUtils.setField(staffTarget, "consultationService", mock(ITcmConsultationService.class));
        ReflectionTestUtils.setField(staffTarget, "appointmentService", mock(ITcmAppointmentService.class));
        staff = proxy(staffTarget, TcmPatientController.class);
        TcmConsentController publicTarget = new TcmConsentController();
        ReflectionTestUtils.setField(publicTarget, "patientService", patients);
        ReflectionTestUtils.setField(publicTarget, "auditLogService", audit);
        publicConsent = proxy(publicTarget, TcmConsentController.class);
        loginAsAdmin();
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void auditFailureRollsBackSignatureAndTokenSoTheSameRequestCanBeRetried(boolean publicFlow)
    {
        String original = jdbc.queryForObject("SELECT body FROM patients", String.class);
        failAudit = true;

        assertThrows(IllegalStateException.class, () -> sign(publicFlow));

        assertEquals(original, jdbc.queryForObject("SELECT body FROM patients", String.class),
                "A failed response must leave signature, token, clinical payload, and PDF metadata unchanged");
        assertEquals(0, auditCount(), "The failed audit insert must also roll back");
        failAudit = false;
        sign(publicFlow);
        assertCommitted();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void successfulSignatureAndAuditCommitTogether(boolean publicFlow)
    {
        Map<String, Object> result = sign(publicFlow);
        assertEquals(true, result.get(publicFlow ? "ok" : "consentSigned"));
        assertCommitted();
    }

    @ParameterizedTest @ValueSource(strings = {"unknown", "already-signed"})
    void invalidOrAlreadySignedPublicTokenDoesNotWriteSignatureOrAudit(String state)
    {
        String token = "wrong-token";
        if ("already-signed".equals(state))
        {
            TcmPatient patient = patient(false); patient.setConsentSigned(1);
            jdbc.update("UPDATE patients SET body = ?", JSON.toJSONString(patient));
            token = "original-token";
        }
        String submittedToken = token;
        String original = jdbc.queryForObject("SELECT body FROM patients", String.class);

        assertThrows(ServiceException.class, () -> publicConsent.signConsent(submittedToken, signature()));

        assertEquals(original, jdbc.queryForObject("SELECT body FROM patients", String.class));
        assertEquals(0, auditCount());
    }

    private Map<String, Object> sign(boolean publicFlow)
    {
        return publicFlow ? publicConsent.signConsent("original-token", signature()).getBody()
                : staff.consent("patient", signature());
    }

    private Map<String, Object> signature()
    {
        Map<String, Object> acknowledgements = new LinkedHashMap<>();
        ConsentDocumentTemplate.getSectionKeys().forEach(key -> acknowledgements.put(key, true));
        return Map.of("signatureName", "Synthetic Signature", "sectionAcknowledgements", acknowledgements);
    }

    private void assertCommitted()
    {
        TcmPatient saved = patient(false);
        assertEquals(1, saved.getConsentSigned());
        assertNotNull(saved.getConsentSignedAt());
        assertNull(saved.getConsentToken());
        assertNull(saved.getConsentTokenExpires());
        assertEquals("Synthetic Signature", JSON.parseObject(saved.getPayload()).getString("consentSignatureName"));
        assertEquals("synthetic/signed.pdf", JSON.parseObject(saved.getPayload()).getString("consentPdfPath"));
        assertEquals("Preserved clinical history", JSON.parseObject(saved.getPayload()).getString("notes"));
        assertEquals(1, auditCount());
    }

    private int auditCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class); }

    private TcmPatient patient(boolean lock)
    {
        return JSON.parseObject(jdbc.queryForObject("SELECT body FROM patients WHERE id='patient'"
                + (lock ? " FOR UPDATE" : ""), String.class), TcmPatient.class);
    }

    private <T> T proxy(Object target, Class<T> type)
    {
        ProxyFactory factory = new ProxyFactory(target);
        if (!type.isInterface()) factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return type.cast(factory.getProxy());
    }

    private void loginAsAdmin()
    {
        SysUser user = new SysUser(); user.setUserId(1L);
        SysRole role = new SysRole(); role.setRoleId(1L); role.setRoleKey("admin");
        user.setRoles(Collections.singletonList(role));
        LoginUser login = new LoginUser(user, Collections.emptySet()); login.setUserId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, Collections.emptyList()));
    }
}
