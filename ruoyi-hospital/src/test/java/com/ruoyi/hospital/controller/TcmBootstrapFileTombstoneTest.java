package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.mapper.TcmPatientFileMapper;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/** The actual bootstrap import endpoint and mapper SQL, with no Spring startup or real files. */
class TcmBootstrapFileTombstoneTest
{
    private static final String DELETED_PATH = "hospital-private/fixture/deleted.pdf";
    private static final String ACTIVE_PATH = "hospital-private/fixture/active.pdf";
    private SqlSession session;
    private JdbcTemplate jdbc;
    private TcmPatientFileMapper mapper;
    private TcmBootstrapController controller;

    @BeforeEach void setup() throws Exception
    {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE tcm_patient_file (id BIGINT AUTO_INCREMENT PRIMARY KEY, patient_id VARCHAR(64),"
                + "consultation_id VARCHAR(64), file_type VARCHAR(64), file_name VARCHAR(128), file_path VARCHAR(512), upload_time TIMESTAMP)");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.getTypeAliasRegistry().registerAlias("TcmPatientFile", TcmPatientFile.class);
        String resource = "mapper/hospital/TcmPatientFileMapper.xml";
        String xml;
        try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resource))
        {
            assertNotNull(stream);
            xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("sysdate()", "CURRENT_TIMESTAMP");
        }
        new XMLMapperBuilder(new StringReader(xml), config, resource, config.getSqlFragments()).parse();
        session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        mapper = session.getMapper(TcmPatientFileMapper.class);
        controller = new TcmBootstrapController();
        ReflectionTestUtils.setField(controller, "patientFileMapper", mapper);
        jdbc.update("INSERT INTO tcm_patient_file (id,patient_id,consultation_id,file_type,file_name,file_path) VALUES (11,'patient-a','visit-a','__deleted__','deleted.pdf',?)", DELETED_PATH);
        jdbc.update("INSERT INTO tcm_patient_file (id,patient_id,consultation_id,file_type,file_name,file_path) VALUES (12,'patient-a','visit-a','document','active.pdf',?)", ACTIVE_PATH);
    }

    @AfterEach void close() { if (session != null) session.close(); }

    private Map<String, Object> importFile(Long id, String path, String fileType)
    {
        Map<String, Object> file = new LinkedHashMap<>();
        if (id != null) file.put("id", id);
        file.put("patientId", "patient-a");
        file.put("consultationId", "visit-a");
        file.put("fileType", fileType);
        file.put("fileName", "old-backup.pdf");
        file.put("filePath", path);
        byte[] data = JSON.toJSONBytes(Map.of("patientFiles", List.of(file)));
        return controller.importData(new MockMultipartFile("file", "synthetic-backup.json", "application/json", data));
    }

    private void assertOriginalRecordsUnchanged()
    {
        session.clearCache();
        TcmPatientFile deleted = mapper.selectTcmPatientFileById(11L);
        assertTrue(deleted.isDeleted());
        assertEquals(DELETED_PATH, deleted.getFilePath());
        assertEquals("deleted.pdf", deleted.getFileName());
        assertEquals(ACTIVE_PATH, mapper.selectTcmPatientFileById(12L).getFilePath());
        assertEquals(List.of(12L), mapper.selectFilesByPatientId("patient-a").stream().map(TcmPatientFile::getId).collect(java.util.stream.Collectors.toList()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tcm_patient_file", Integer.class));
    }

    @Test void staleBackupWithDeletedFileIdCannotReactivateOrReplaceTheTombstone()
    {
        assertEquals(0, importFile(11L, DELETED_PATH, "document").get("restored"));
        assertOriginalRecordsUnchanged();
    }

    @Test void staleBackupWithMissingOrDifferentIdCannotReinsertADeletedPath()
    {
        assertEquals(0, importFile(null, DELETED_PATH, "document").get("restored"));
        assertEquals(0, importFile(99L, DELETED_PATH, "document").get("restored"));
        assertEquals(0, importFile(99L, "/" + DELETED_PATH, "document").get("restored"));
        assertOriginalRecordsUnchanged();
    }

    @Test void activeIdCollisionCannotBypassTheDeletedPathGuard()
    {
        // Backup IDs may come from a different database; an active local ID must not win over a deleted path.
        assertEquals(0, importFile(12L, DELETED_PATH, "document").get("restored"));
        assertOriginalRecordsUnchanged();
    }

    @Test void validUnindexedFileCanStillBeRestoredWhileIncomingDeletionMarkersAreIgnored()
    {
        String newPath = "hospital-private/legacy/unindexed.pdf";
        assertEquals(0, importFile(99L, newPath, "__deleted__").get("restored"));
        assertNull(mapper.selectTcmPatientFileById(99L));
        assertEquals(1, importFile(99L, newPath, "document").get("restored"));
        session.clearCache();
        assertEquals(newPath, mapper.selectTcmPatientFileById(99L).getFilePath());
        assertFalse(mapper.selectTcmPatientFileById(99L).isDeleted());
        assertTrue(mapper.selectTcmPatientFileById(11L).isDeleted());
        assertEquals(2, mapper.selectFilesByPatientId("patient-a").size());
    }
}
