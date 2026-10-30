package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.impl.TcmPatientServiceImpl;
import com.ruoyi.hospital.utils.PayloadUtils;

/** Real patient endpoint/service/SQL with an isolated in-memory database; no application startup. */
class TcmPatientUpdatePersistenceTest
{
    private SqlSession session;
    private TcmPatientMapper mapper;
    private TcmPatientController controller;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception
    {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE tcm_patient (id VARCHAR(64) PRIMARY KEY, name VARCHAR(128), first_name VARCHAR(64),"
                + "last_name VARCHAR(64), email VARCHAR(254), phone VARCHAR(64), practitioner_id VARCHAR(64),"
                + "is_active INT, consent_signed INT, consent_signed_at VARCHAR(32), consent_token VARCHAR(64),"
                + "consent_token_expires VARCHAR(32), merged_into VARCHAR(64), deleted_at VARCHAR(32),"
                + "payload VARCHAR(20000), create_time TIMESTAMP, update_time TIMESTAMP)");
        insertPatient("person-a", "Alice", "Lee");
        insertPatient("person-b", "Bob", "Lee");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.getTypeAliasRegistry().registerAlias("TcmPatient", TcmPatient.class);
        String resource = "mapper/hospital/TcmPatientMapper.xml";
        String xml;
        try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resource))
        {
            assertNotNull(stream);
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("sysdate()", "CURRENT_TIMESTAMP");
        }
        new XMLMapperBuilder(new StringReader(xml), config, resource, config.getSqlFragments()).parse();
        session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        mapper = session.getMapper(TcmPatientMapper.class);
        TcmPatientServiceImpl service = new TcmPatientServiceImpl();
        ReflectionTestUtils.setField(service, "tcmPatientMapper", mapper);
        controller = new TcmPatientController();
        ReflectionTestUtils.setField(controller, "patientService", service);
        ReflectionTestUtils.setField(controller, "auditLogService", mock(ITcmAuditLogService.class));
        ReflectionTestUtils.setField(controller, "appointmentService", mock(ITcmAppointmentService.class));
        ReflectionTestUtils.setField(controller, "consultationService", mock(ITcmConsultationService.class));
        SysUser user = new SysUser();
        user.setUserId(1L);
        SysRole role = new SysRole();
        role.setRoleKey("admin");
        user.setRoles(Collections.singletonList(role));
        LoginUser login = new LoginUser(user, Collections.emptySet());
        login.setUserId(1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, Collections.emptyList()));
    }

    private void insertPatient(String id, String first, String last)
    {
        String payload = JSON.toJSONString(Map.of("emails", List.of("family@example.test"), "addressStreet", "Synthetic street",
                "latestIntakeFormData", Map.of("chiefComplaint", "Synthetic history"), "linkedUserId", id,
                "notes", "Existing note", "intakeToken", "synthetic-intake-" + id, "tags", List.of("old")));
        jdbc.update("INSERT INTO tcm_patient (id,name,first_name,last_name,email,phone,practitioner_id,is_active,"
                + "consent_signed,consent_signed_at,consent_token,consent_token_expires,payload) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, last + " " + first, first, last, "family@example.test", "5550100", "doctor-1", 0,
                1, "2026-10-01 09:00:00", "synthetic-consent-" + id, "2026-11-01 09:00:00", payload);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
        if (session != null) session.close();
    }

    @Test
    void sparseHistoryEditPreservesConsentStateContactAndIntake()
    {
        controller.update("person-a", Map.of("historyAndMedication", "Updated synthetic history"));
        TcmPatient saved = mapper.selectTcmPatientById("person-a");
        JSONObject payload = JSON.parseObject(saved.getPayload());
        assertAll(
                () -> assertEquals(1, saved.getConsentSigned()),
                () -> assertEquals(0, saved.getIsActive()),
                () -> assertEquals("family@example.test", saved.getEmail()),
                () -> assertEquals("Lee Alice", saved.getName()),
                () -> assertEquals("synthetic-consent-person-a", saved.getConsentToken()),
                () -> assertEquals("Synthetic street", payload.getString("addressStreet")),
                () -> assertNotNull(payload.getJSONObject("latestIntakeFormData")),
                () -> assertEquals("person-a", payload.getString("linkedUserId")),
                () -> assertEquals("synthetic-intake-person-a", payload.getString("intakeToken")),
                () -> assertEquals("Updated synthetic history", payload.getString("historyAndMedication")));
    }

    @Test
    void sparseEditCannotRestoreAnOlderPayloadEmailOverTheCurrentPrimaryEmail()
    {
        jdbc.update("UPDATE tcm_patient SET email=? WHERE id=?", "current@example.test", "person-a");
        controller.update("person-a", Map.of("notes", "Updated note"));
        assertEquals("current@example.test", mapper.selectTcmPatientById("person-a").getEmail());
    }

    @Test
    void explicitPrimaryEmailAndEmailListEditsRemainSupported()
    {
        controller.update("person-a", Map.of("email", "changed@example.test"));
        assertEquals("changed@example.test", mapper.selectTcmPatientById("person-a").getEmail());
        controller.update("person-a", Map.of("emails", List.of("next@example.test", "extra@example.test")));
        TcmPatient saved = mapper.selectTcmPatientById("person-a");
        assertEquals("next@example.test", saved.getEmail());
        assertEquals(List.of("next@example.test", "extra@example.test"), JSON.parseObject(saved.getPayload()).getJSONArray("emails"));
        assertEquals(1, saved.getConsentSigned());
    }

    @Test
    void explicitPayloadClearsAndCollectionReplacementsKeepUnmentionedData()
    {
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("notes", "");
        edit.put("addressStreet", null);
        edit.put("tags", Collections.emptyList());
        controller.update("person-a", edit);
        JSONObject payload = JSON.parseObject(mapper.selectTcmPatientById("person-a").getPayload());
        assertEquals("", payload.getString("notes"));
        assertNull(payload.getString("addressStreet"));
        assertTrue(payload.getJSONArray("tags").isEmpty());
        assertNotNull(payload.getJSONObject("latestIntakeFormData"));
        assertEquals("person-a", payload.getString("linkedUserId"));
    }

    @Test
    void idBoundEditLeavesAnotherHouseholdMemberWithTheSameEmailUntouched()
    {
        TcmPatient before = mapper.selectTcmPatientById("person-b");
        controller.update("person-a", Map.of("firstName", "Alicia", "name", "Lee Alicia", "notes", "Changed"));
        TcmPatient after = mapper.selectTcmPatientById("person-b");
        assertEquals(JSON.toJSONString(before), JSON.toJSONString(after));
        assertEquals("Alicia", mapper.selectTcmPatientById("person-a").getFirstName());
    }

    @Test
    void fullProfileEditsStillApplyExplicitValuesAndCannotRedirectTheTargetId()
    {
        Map<String, Object> edit = PayloadUtils.flatten(mapper.selectTcmPatientById("person-a"));
        edit.put("id", "person-b");
        edit.put("name", "Lee Alicia");
        edit.put("firstName", "Alicia");
        edit.put("isActive", true);
        edit.put("notes", "Updated complete profile");
        controller.update("person-a", edit);
        TcmPatient saved = mapper.selectTcmPatientById("person-a");
        assertEquals("Alicia", saved.getFirstName());
        assertEquals(1, saved.getIsActive());
        assertEquals(1, saved.getConsentSigned());
        assertEquals("synthetic-consent-person-a", saved.getConsentToken());
        assertEquals("Updated complete profile", JSON.parseObject(saved.getPayload()).getString("notes"));
        assertEquals("Bob", mapper.selectTcmPatientById("person-b").getFirstName());
    }

    @Test
    void practitionerEditingARedactedProfileCannotClearItsHiddenContacts()
    {
        jdbc.update("UPDATE tcm_patient SET practitioner_id=? WHERE id=?", "51", "person-a");
        SysUser user = new SysUser();
        user.setUserId(51L);
        SysRole role = new SysRole();
        role.setRoleKey("practitioner");
        user.setRoles(List.of(role));
        LoginUser login = new LoginUser(user, Collections.emptySet());
        login.setUserId(51L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, Collections.emptyList()));
        Map<String, Object> edit = new LinkedHashMap<>(controller.get("person-a"));
        edit.put("notes", "Updated clinical note");
        // The current UI initializes these absent, redacted fields for its edit form.
        edit.put("emails", Collections.emptyList());
        edit.put("addressStreet", "");
        edit.put("addressCountry", "CA");
        edit.put("email", "replacement@example.test");
        edit.put("practitionerId", "999");
        Map<String, Object> response = controller.update("person-a", edit);
        TcmPatient saved = mapper.selectTcmPatientById("person-a");
        JSONObject payload = JSON.parseObject(saved.getPayload());
        assertAll(
                () -> assertEquals("family@example.test", saved.getEmail()),
                () -> assertEquals(List.of("family@example.test"), payload.getJSONArray("emails")),
                () -> assertEquals("Synthetic street", payload.getString("addressStreet")),
                () -> assertFalse(payload.containsKey("addressCountry")),
                () -> assertEquals("51", saved.getPractitionerId()),
                () -> assertEquals("Updated clinical note", payload.getString("notes")),
                () -> assertEquals(1, saved.getConsentSigned()),
                () -> assertFalse(response.containsKey("email")),
                () -> assertFalse(response.containsKey("emails")),
                () -> assertFalse(response.containsKey("addressStreet")));
    }

    @Test
    void sparseEditsDoNotReplaceLegacyNullStateWithSerializationDefaults()
    {
        jdbc.update("UPDATE tcm_patient SET is_active=NULL, consent_signed=NULL WHERE id=?", "person-a");
        controller.update("person-a", Map.of("notes", "Updated note"));
        TcmPatient saved = mapper.selectTcmPatientById("person-a");
        assertNull(saved.getIsActive());
        assertNull(saved.getConsentSigned());
    }

    @Test
    void requestCannotReplacePersistedConsentTokenOrExpiry()
    {
        controller.update("person-a", Map.of("notes", "Updated note", "consentToken", "forged-token",
                "consentTokenExpires", "2099-01-01 00:00:00"));
        TcmPatient saved = mapper.selectTcmPatientById("person-a");
        assertEquals("synthetic-consent-person-a", saved.getConsentToken());
        assertEquals("2026-11-01 09:00:00", saved.getConsentTokenExpires());
        assertEquals(1, saved.getConsentSigned());
    }

    @Test
    void fullEditRoundTripCannotRestoreAnOlderPrimaryEmailAlias()
    {
        controller.update("person-a", Map.of("email", "current@example.test"));
        Map<String, Object> edit = new LinkedHashMap<>(controller.get("person-a"));
        edit.put("notes", "Only this note was edited");
        // Match PatientDetailView -> patientsStore.updatePatient: emails[0]
        // becomes the primary email even when the user only changed a note.
        List<?> emails = (List<?>) edit.get("emails");
        edit.put("email", emails.get(0));
        controller.update("person-a", edit);
        TcmPatient saved = mapper.selectTcmPatientById("person-a");
        assertEquals("current@example.test", saved.getEmail());
        assertTrue(JSON.parseObject(saved.getPayload()).getJSONArray("emails").contains("family@example.test"),
                "An existing alternate address must remain available");
    }

    @Test
    void fullEditOpenedBeforeConsentSigningCannotUndoTheNewSignature()
    {
        jdbc.update("UPDATE tcm_patient SET consent_signed=0, consent_signed_at=NULL WHERE id=?", "person-a");
        Map<String, Object> staleEdit = new LinkedHashMap<>(controller.get("person-a"));
        controller.consent("person-a", Map.of("signatureName", "Synthetic signature"));
        TcmPatient signed = mapper.selectTcmPatientById("person-a");
        staleEdit.put("notes", "Only this note was edited");
        controller.update("person-a", staleEdit);
        TcmPatient saved = mapper.selectTcmPatientById("person-a");
        assertEquals(1, saved.getConsentSigned());
        assertEquals(signed.getConsentSignedAt(), saved.getConsentSignedAt());
        assertEquals(JSON.parseObject(signed.getPayload()).getString("consentSignatureName"),
                JSON.parseObject(saved.getPayload()).getString("consentSignatureName"));
    }

    @Test
    void fullEditOpenedBeforeIntakeSubmissionCannotRestoreConsumedTokenOrOldIntake()
    {
        Map<String, Object> staleEdit = new LinkedHashMap<>(controller.get("person-a"));
        TcmPatientServiceImpl service = (TcmPatientServiceImpl) ReflectionTestUtils.getField(controller, "patientService");
        service.saveIntakeFormByToken("synthetic-intake-person-a", Map.of("chiefComplaint", "New synthetic complaint"));
        staleEdit.put("notes", "Only this note was edited");
        controller.update("person-a", staleEdit);
        JSONObject payload = JSON.parseObject(mapper.selectTcmPatientById("person-a").getPayload());
        assertAll(
                () -> assertEquals("New synthetic complaint", payload.getJSONObject("latestIntakeFormData").getString("chiefComplaint")),
                () -> assertFalse(payload.containsKey("intakeToken")),
                () -> assertTrue(payload.getBooleanValue("latestIntakeCompleted")));
    }

    @Test
    void profileEditCannotIntroduceWorkflowFieldsButStillAllowsStaffLinkAndClinicalHistory()
    {
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("consentSigned", false);
        edit.put("consentVersion", "stale-version");
        edit.put("consentPdfPath", "stale.pdf");
        edit.put("consentPdfUrl", "https://example.test/stale.pdf");
        edit.put("consentPdfGeneratedAt", "2020-01-01 00:00:00");
        edit.put("intakeTokenExpires", "2099-01-01 00:00:00");
        edit.put("latestIntakeSubmittedAt", "2020-01-01 00:00:00");
        edit.put("latestIntakeSource", "stale-source");
        edit.put("latestIntakeCompleted", false);
        edit.put("latestIntakeAppointmentId", "stale-appointment");
        edit.put("intakeHistoryAndMedicationSummary", "stale-summary");
        edit.put("staffUserId", "7");
        edit.put("linkedUserId", "7");
        edit.put("isStaffProfile", true);
        edit.put("historyAndMedication", "Explicitly updated history");
        edit.put("historySourceConsultId", "synthetic-consult");
        edit.put("historySourceConsultDate", "2026-10-30");
        controller.update("person-a", edit);
        TcmPatient saved = mapper.selectTcmPatientById("person-a");
        JSONObject payload = JSON.parseObject(saved.getPayload());
        for (String field : List.of("consentVersion", "consentPdfPath", "consentPdfUrl", "consentPdfGeneratedAt",
                "intakeTokenExpires", "latestIntakeSubmittedAt", "latestIntakeSource", "latestIntakeCompleted",
                "latestIntakeAppointmentId", "intakeHistoryAndMedicationSummary")) assertFalse(payload.containsKey(field), field);
        assertEquals(1, saved.getConsentSigned());
        assertEquals("7", payload.getString("staffUserId"));
        assertEquals("7", payload.getString("linkedUserId"));
        assertTrue(payload.getBooleanValue("isStaffProfile"));
        assertEquals("Explicitly updated history", payload.getString("historyAndMedication"));
        assertEquals("synthetic-consult", payload.getString("historySourceConsultId"));
        assertEquals("2026-10-30", payload.getString("historySourceConsultDate"));
    }
}
