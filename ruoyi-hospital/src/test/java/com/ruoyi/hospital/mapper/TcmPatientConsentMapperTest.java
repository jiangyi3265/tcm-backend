package com.ruoyi.hospital.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
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
import com.ruoyi.hospital.domain.TcmPatient;

class TcmPatientConsentMapperTest {
    private SqlSession session;
    private TcmPatientMapper mapper;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE tcm_patient (id VARCHAR(64) PRIMARY KEY, name VARCHAR(128), first_name VARCHAR(128),"
                + "last_name VARCHAR(128), email VARCHAR(128), phone VARCHAR(64), gender VARCHAR(32), birth_date VARCHAR(32),"
                + "is_active INT, practitioner_id VARCHAR(64), consent_signed INT, consent_signed_at VARCHAR(32),"
                + "consent_token VARCHAR(64), consent_token_expires VARCHAR(32), merged_into VARCHAR(64),"
                + "deleted_at VARCHAR(32), payload VARCHAR(10000), create_time TIMESTAMP, update_time TIMESTAMP)");
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.getTypeAliasRegistry().registerAlias("TcmPatient", TcmPatient.class);
        String resource = "mapper/hospital/TcmPatientMapper.xml";
        String xml;
        try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream); xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        new XMLMapperBuilder(new StringReader(xml), configuration, resource, configuration.getSqlFragments()).parse();
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        mapper = session.getMapper(TcmPatientMapper.class);
    }

    @AfterEach void cleanup() { if (session != null) session.close(); }

    @Test void legacyMergedDuplicateTokenResolvesOnlyToTheKeptProfile() {
        jdbc.update("INSERT INTO tcm_patient(id,consent_token) VALUES (?,?)", "keep", "shared-token");
        jdbc.update("INSERT INTO tcm_patient(id,consent_token,merged_into,deleted_at) VALUES (?,?,?,?)",
                "merged", "shared-token", "keep", "2026-01-01 00:00:00");
        assertEquals("keep", mapper.selectTcmPatientByConsentToken("shared-token").getId());
    }

    @Test void deletedAndMergedOnlyTokensCannotOpenRetiredRecords() {
        jdbc.update("INSERT INTO tcm_patient(id,consent_token,deleted_at) VALUES (?,?,?)",
                "deleted", "deleted-token", "2026-01-01 00:00:00");
        jdbc.update("INSERT INTO tcm_patient(id,consent_token,merged_into) VALUES (?,?,?)",
                "merged", "merged-token", "keep");
        assertNull(mapper.selectTcmPatientByConsentToken("deleted-token"));
        assertNull(mapper.selectTcmPatientByConsentToken("merged-token"));
    }
}
