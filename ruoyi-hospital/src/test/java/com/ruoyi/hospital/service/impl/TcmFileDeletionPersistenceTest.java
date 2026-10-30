package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.hospital.controller.TcmFileAccessController;
import com.ruoyi.hospital.controller.TcmFileController;
import com.ruoyi.hospital.controller.TcmPatientController;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.hospital.util.CloudflareR2StorageService;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.mapper.*;
import com.ruoyi.hospital.service.*;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.PatientFileReferences;
import com.ruoyi.hospital.util.SignedFileUrlService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/** Real mapper SQL + file/consultation services, with synthetic local bytes only. */
class TcmFileDeletionPersistenceTest
{
    private static final String RESOURCE = "hospital-private/fixture/saved.pdf";
    private SqlSession session;
    private JdbcTemplate jdbc;
    private TcmPatientFileMapper fileMapper;
    private TcmConsultationMapper consultationMapper;
    private TcmPatientMapper patientMapper;
    private TcmPatientFileServiceImpl files;
    private TcmConsultationServiceImpl consultations;
    @TempDir Path directory;

    @BeforeEach void setup() throws Exception
    {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE tcm_patient_file (id BIGINT AUTO_INCREMENT PRIMARY KEY, patient_id VARCHAR(64), consultation_id VARCHAR(64), file_type VARCHAR(64), file_name VARCHAR(128), file_path VARCHAR(512), upload_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE tcm_consultation (id VARCHAR(64) PRIMARY KEY, consultation_id VARCHAR(64), patient_id VARCHAR(64), practitioner_id VARCHAR(64), consult_date VARCHAR(32), status VARCHAR(32), branch_id VARCHAR(64), locked_at VARCHAR(32), version INT, deleted_at VARCHAR(32), payload VARCHAR(20000), create_time TIMESTAMP, update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE tcm_patient (id VARCHAR(64) PRIMARY KEY,name VARCHAR(128),first_name VARCHAR(64),last_name VARCHAR(64),email VARCHAR(254),phone VARCHAR(64),practitioner_id VARCHAR(64),is_active INT,consent_signed INT,consent_signed_at VARCHAR(32),consent_token VARCHAR(64),consent_token_expires VARCHAR(32),merged_into VARCHAR(64),deleted_at VARCHAR(32),payload VARCHAR(20000),create_time TIMESTAMP,update_time TIMESTAMP)");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        for (Class<?> type : List.of(TcmPatientFile.class, TcmConsultation.class, TcmPatient.class))
            config.getTypeAliasRegistry().registerAlias(type.getSimpleName(), type);
        for (String name : List.of("TcmPatientFileMapper", "TcmConsultationMapper", "TcmPatientMapper"))
        {
            String resource = "mapper/hospital/" + name + ".xml";
            String xml;
            try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resource))
            { xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
            xml = xml.replace("sysdate()", "CURRENT_TIMESTAMP").replace(" as binary)", " as varbinary)");
            new XMLMapperBuilder(new StringReader(xml), config, resource, config.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        fileMapper = session.getMapper(TcmPatientFileMapper.class);
        consultationMapper = session.getMapper(TcmConsultationMapper.class);
        patientMapper = session.getMapper(TcmPatientMapper.class);
        files = new TcmPatientFileServiceImpl();
        ReflectionTestUtils.setField(files, "tcmPatientFileMapper", fileMapper);
        ReflectionTestUtils.setField(files, "consultationMapper", consultationMapper);
        ReflectionTestUtils.setField(files, "patientMapper", patientMapper);
        consultations = new TcmConsultationServiceImpl();
        ReflectionTestUtils.setField(consultations, "consultationMapper", consultationMapper);
        ReflectionTestUtils.setField(consultations, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(consultations, "patientFileService", files);
        ReflectionTestUtils.setField(consultations, "inventoryService", mock(ITcmInventoryService.class));
        jdbc.update("INSERT INTO tcm_patient (id,name,is_active,consent_signed,payload) VALUES ('patient-a','Synthetic',1,1,?)", payload());
        jdbc.update("INSERT INTO tcm_patient (id,name,is_active,consent_signed,payload) VALUES ('patient-b','Other',1,1,?)", payload());
        insertConsultation("consultation-a", "patient-a", payload());
        insertConsultation("consultation-b", "patient-b", payload());
        jdbc.update("INSERT INTO tcm_patient_file (id,patient_id,consultation_id,file_type,file_name,file_path) VALUES (11,'patient-a','consultation-a','document','saved.pdf',?)", RESOURCE);
        SysUser user = new SysUser(); user.setUserId(1L);
        SysRole role = new SysRole(); role.setRoleKey("admin"); user.setRoles(List.of(role));
        LoginUser login = new LoginUser(user, java.util.Collections.emptySet()); login.setUserId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null, List.of()));
    }

    @AfterEach void close() { SecurityContextHolder.clearContext(); if (session != null) session.close(); }

    private String payload()
    {
        return JSON.toJSONString(Map.of("documents", List.of(Map.of("id",11,"name","saved.pdf","resource",RESOURCE)),
                "notes","unchanged clinical note","prescriptions",List.of(),"totalAmount",123));
    }

    private void insertConsultation(String id, String patient, String payload)
    {
        jdbc.update("INSERT INTO tcm_consultation (id,consultation_id,patient_id,consult_date,status,version,payload) VALUES (?,?,?,'2026-10-30','draft',7,?)", id,id,patient,payload);
    }

    @Test void deleteEndpointReopenAndStaleSaveCannotResurrectTheDocument() throws Exception
    {
        TcmConsultation stale = consultationMapper.selectTcmConsultationById("consultation-a");
        Path physical = directory.resolve("saved.pdf"); Files.writeString(physical, "synthetic");
        HospitalFileStorage storage = mock(HospitalFileStorage.class);
        when(storage.resolve(RESOURCE)).thenReturn(physical);
        TcmFileController controller = new TcmFileController();
        ReflectionTestUtils.setField(controller,"fileService",files);
        ReflectionTestUtils.setField(controller,"hospitalFileStorage",storage);
        ReflectionTestUtils.setField(controller,"consultationService",consultations);
        ITcmPatientService patients=mock(ITcmPatientService.class);
        when(patients.selectTcmPatientById(anyString())).thenAnswer(call -> patientMapper.selectTcmPatientById(call.getArgument(0)));
        ReflectionTestUtils.setField(controller,"patientService",patients);
        ReflectionTestUtils.setField(controller,"appointmentService",mock(ITcmAppointmentService.class));
        ReflectionTestUtils.setField(controller,"auditLogService",mock(ITcmAuditLogService.class));
        assertEquals(true,controller.remove(11L).get("ok"));
        assertFalse(Files.exists(physical));
        assertTrue(files.selectFilesByConsultationId("consultation-a").isEmpty());
        assertTrue(fileMapper.selectAllTcmPatientFiles().isEmpty());
        assertTrue(JSON.parseObject(consultations.selectTcmConsultationById("consultation-a").getPayload()).getJSONArray("documents").isEmpty());
        TcmConsultation saved = consultationMapper.selectTcmConsultationById("consultation-a");
        assertEquals(7,saved.getVersion()); assertEquals("draft",saved.getStatus());
        assertEquals("unchanged clinical note",JSON.parseObject(saved.getPayload()).getString("notes"));
        assertTrue(JSON.parseObject(patientMapper.selectTcmPatientById("patient-a").getPayload()).getJSONArray("documents").isEmpty());
        assertEquals(1,patientMapper.selectTcmPatientById("patient-a").getConsentSigned());
        assertEquals(payload(),consultationMapper.selectTcmConsultationById("consultation-b").getPayload());
        assertEquals(payload(),patientMapper.selectTcmPatientById("patient-b").getPayload());
        consultations.updateTcmConsultation(stale,"1");
        assertTrue(JSON.parseObject(consultationMapper.selectTcmConsultationById("consultation-a").getPayload()).getJSONArray("documents").isEmpty());
        assertTrue(files.selectFilesByPatientId("patient-a").isEmpty());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tcm_patient_file",Integer.class));
        assertTrue(fileMapper.selectTcmPatientFileById(11L).isDeleted());
        assertEquals(true,controller.remove(11L).get("ok"));
    }

    @Test void uploadBeforeConsultationExistsIsRemovedFromLaterSavedReferences()
    {
        jdbc.update("UPDATE tcm_patient_file SET consultation_id=NULL WHERE id=11");
        files.deleteTcmPatientFileById(11L);
        assertTrue(JSON.parseObject(consultationMapper.selectTcmConsultationById("consultation-a").getPayload()).getJSONArray("documents").isEmpty());
        TcmConsultation later = new TcmConsultation(); later.setId("later"); later.setConsultationId("later");
        later.setPatientId("patient-a"); later.setStatus("draft"); later.setPayload(payload());
        consultations.insertTcmConsultation(later);
        assertTrue(JSON.parseObject(consultationMapper.selectTcmConsultationById("later").getPayload()).getJSONArray("documents").isEmpty());
        assertTrue(files.selectFilesByConsultationId("later").isEmpty());
    }

    @Test void unindexedLegacyAttachmentIsPreservedAndIndexedNormally()
    {
        TcmConsultation current=consultationMapper.selectTcmConsultationById("consultation-a");
        String resource="hospital-private/legacy/unindexed.pdf";
        current.setPayload(JSON.toJSONString(Map.of("documents",List.of(Map.of("resource",resource,"name","legacy.pdf")))));
        consultations.updateTcmConsultation(current,"1");
        assertNotNull(fileMapper.selectTcmPatientFileByPath(resource));
        assertEquals(1,JSON.parseObject(consultationMapper.selectTcmConsultationById("consultation-a").getPayload()).getJSONArray("documents").size());
    }

    @Test void oldSignedLinkCannotRestoreDeletedBytesButUnindexedLinksStillWork() throws Exception
    {
        files.deleteTcmPatientFileById(11L);
        SignedFileUrlService signer=mock(SignedFileUrlService.class);
        when(signer.isValid(anyString(),anyLong(),anyString())).thenReturn(true);
        HospitalFileStorage storage=mock(HospitalFileStorage.class);
        TcmFileAccessController controller=new TcmFileAccessController(signer,storage,files);
        MockHttpServletResponse response=new MockHttpServletResponse();
        controller.access(RESOURCE,Long.MAX_VALUE,"synthetic",response);
        assertEquals(404,response.getStatus()); verifyNoInteractions(storage);
        Path legacy=directory.resolve("legacy.pdf"); Files.writeString(legacy,"synthetic legacy bytes");
        when(storage.resolve("hospital-private/legacy.pdf")).thenReturn(legacy);
        MockHttpServletResponse valid=new MockHttpServletResponse();
        controller.access("hospital-private/legacy.pdf",Long.MAX_VALUE,"synthetic",valid);
        assertEquals(200,valid.getStatus()); assertEquals("synthetic legacy bytes",valid.getContentAsString());
    }

    @Test void casDoesNotOverwriteNewerPayloadAndMetadataCannotReviveTombstone()
    {
        String old=consultationMapper.selectTcmConsultationById("consultation-a").getPayload();
        jdbc.update("UPDATE tcm_consultation SET payload=?,version=9,status='completed' WHERE id='consultation-a'","{\"notes\":\"newer\"}");
        TcmConsultation update=new TcmConsultation(); update.setId("consultation-a"); update.setPayload("{}");
        assertEquals(0,consultationMapper.updatePdfPayloadIfUnchanged(update,old));
        session.clearCache();
        files.deleteTcmPatientFileById(11L);
        TcmConsultation saved=consultationMapper.selectTcmConsultationById("consultation-a");
        assertEquals(9,saved.getVersion()); assertEquals("completed",saved.getStatus());
        assertEquals("{\"notes\":\"newer\"}",saved.getPayload());
        TcmPatientFile stale=new TcmPatientFile(); stale.setId(11L); stale.setFileType("document"); stale.setFilePath(RESOURCE);
        assertEquals(0,files.updateTcmPatientFile(stale));
        assertEquals(0,files.insertTcmPatientFile(stale));
        assertTrue(fileMapper.selectTcmPatientFileById(11L).isDeleted());
    }

    @Test void matchingIsExactAndDoesNotTreatClinicalIdsOrTextAsFiles()
    {
        TcmPatientFile deleted=fileMapper.selectTcmPatientFileById(11L);
        JSONObject payload=JSON.parseObject("{\"documents\":[{\"id\":11},{\"resource\":\"hospital-private/fixture/saved.pdf.other\"}],\"prescriptions\":[{\"id\":11,\"notes\":\"hospital-private/fixture/saved.pdf\",\"resource\":\"hospital-private/fixture/saved.pdf\"}],\"diff\":{\"tongueImage\":\"/api/public/files/access?resource=hospital-private%2Ffixture%2Fsaved.pdf&signature=synthetic\"}}");
        assertTrue(PatientFileReferences.removeDeleted(payload,List.of(deleted)));
        assertEquals(1,payload.getJSONArray("documents").size());
        assertEquals(1,payload.getJSONArray("prescriptions").size());
        assertEquals(RESOURCE,payload.getJSONArray("prescriptions").getJSONObject(0).getString("notes"));
        assertFalse(payload.getJSONArray("prescriptions").getJSONObject(0).containsKey("resource"));
        assertFalse(payload.getJSONObject("diff").containsKey("tongueImage"));
    }

    private TcmFileController controller(ITcmPatientFileService service, HospitalFileStorage storage)
    {
        TcmFileController result=new TcmFileController();
        ReflectionTestUtils.setField(result,"fileService",service);
        ReflectionTestUtils.setField(result,"hospitalFileStorage",storage);
        ReflectionTestUtils.setField(result,"consultationService",consultations);
        ReflectionTestUtils.setField(result,"auditLogService",mock(ITcmAuditLogService.class));
        ReflectionTestUtils.setField(result,"appointmentService",mock(ITcmAppointmentService.class));
        ITcmPatientService patients=mock(ITcmPatientService.class);
        when(patients.selectTcmPatientById(anyString())).thenAnswer(call -> patientMapper.selectTcmPatientById(call.getArgument(0)));
        ReflectionTestUtils.setField(result,"patientService",patients);
        return result;
    }

    @Test void databaseFailureLeavesOriginalFileUntouched() throws Exception
    {
        Path physical=directory.resolve("original.pdf"); Files.writeString(physical,"original bytes");
        ITcmPatientFileService failed=mock(ITcmPatientFileService.class);
        when(failed.selectTcmPatientFileById(11L)).thenReturn(fileMapper.selectTcmPatientFileById(11L));
        when(failed.deleteTcmPatientFileById(11L)).thenThrow(new ServiceException("CAS conflict"));
        HospitalFileStorage storage=mock(HospitalFileStorage.class);
        assertThrows(ServiceException.class,() -> controller(failed,storage).remove(11L));
        assertEquals("original bytes",Files.readString(physical)); verifyNoInteractions(storage);
        assertFalse(fileMapper.selectTcmPatientFileById(11L).isDeleted());
    }

    @Test void physicalCleanupFailureKeepsDeletionEffectiveAndCanBeRetried() throws Exception
    {
        Path physical=directory.resolve("nonempty"); Files.createDirectories(physical);
        Path child=physical.resolve("child"); Files.writeString(child,"synthetic");
        HospitalFileStorage storage=mock(HospitalFileStorage.class); when(storage.resolve(RESOURCE)).thenReturn(physical);
        TcmFileController controller=controller(files,storage);
        Map<String,Object> result=controller.remove(11L);
        assertEquals(true,result.get("ok")); assertEquals(true,result.get("physicalCleanupPending"));
        assertTrue(fileMapper.selectTcmPatientFileById(11L).isDeleted());
        assertTrue(files.selectFilesByPatientId("patient-a").isEmpty());
        Files.delete(child);
        assertFalse(controller.remove(11L).containsKey("physicalCleanupPending"));
        assertFalse(Files.exists(physical));
    }

    @Test void allStorageRestoreCallersRespectTheTombstone()
    {
        files.deleteTcmPatientFileById(11L);
        HospitalFileStorage storage=new HospitalFileStorage();
        CloudflareR2StorageService cloud=mock(CloudflareR2StorageService.class);
        ReflectionTestUtils.setField(storage,"patientFileMapper",fileMapper);
        ReflectionTestUtils.setField(storage,"r2StorageService",cloud);
        assertFalse(storage.restoreResource(RESOURCE));
        assertFalse(storage.restoreResource("/"+RESOURCE));
        verifyNoInteractions(cloud);
    }

    @Test void stalePatientPutCannotRestoreDeletedDocumentReferences()
    {
        Map<String,Object> stale=PayloadUtils.flatten(patientMapper.selectTcmPatientById("patient-a"));
        files.deleteTcmPatientFileById(11L);
        TcmPatientServiceImpl patients=new TcmPatientServiceImpl();
        ReflectionTestUtils.setField(patients,"tcmPatientMapper",patientMapper);
        ReflectionTestUtils.setField(patients,"patientFileService",files);
        TcmPatientController controller=new TcmPatientController();
        ReflectionTestUtils.setField(controller,"patientService",patients);
        ReflectionTestUtils.setField(controller,"consultationService",consultations);
        ReflectionTestUtils.setField(controller,"appointmentService",mock(ITcmAppointmentService.class));
        ReflectionTestUtils.setField(controller,"auditLogService",mock(ITcmAuditLogService.class));
        stale.put("notes","new note"); controller.update("patient-a",stale);
        JSONObject result=JSON.parseObject(patientMapper.selectTcmPatientById("patient-a").getPayload());
        assertTrue(result.getJSONArray("documents").isEmpty()); assertEquals("new note",result.getString("notes"));
    }

    @Test void transferredTombstoneStillFiltersTheMergedPatientsStaleDocuments()
    {
        files.deleteTcmPatientFileById(11L);
        assertEquals(1,files.reassignDeletedFiles("patient-a","patient-b"));
        assertTrue(files.selectDeletedFilesByPatientId("patient-a").isEmpty());
        assertEquals(1,files.selectDeletedFilesByPatientId("patient-b").size());
        assertTrue(fileMapper.selectTcmPatientFileById(11L).isDeleted());
        TcmConsultation stale=consultationMapper.selectTcmConsultationById("consultation-b");
        consultations.updateTcmConsultation(stale,"1");
        assertTrue(JSON.parseObject(consultationMapper.selectTcmConsultationById("consultation-b").getPayload()).getJSONArray("documents").isEmpty());
        assertTrue(files.selectFilesByPatientId("patient-b").isEmpty());
    }
}
