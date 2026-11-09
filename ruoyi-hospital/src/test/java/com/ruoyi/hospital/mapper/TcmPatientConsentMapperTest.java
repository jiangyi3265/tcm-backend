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
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import java.util.concurrent.*;

class TcmPatientConsentMapperTest {
    private SqlSession session;
    private TcmPatientMapper mapper;
    private JdbcTemplate jdbc;
    private JdbcDataSource source;
    private SqlSessionFactory factory;

    @BeforeEach void setup() throws Exception {
        source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE tcm_patient (id VARCHAR(64) PRIMARY KEY, name VARCHAR(128), first_name VARCHAR(128),"
                + "last_name VARCHAR(128), email VARCHAR(128), phone VARCHAR(64), gender VARCHAR(32), birth_date VARCHAR(32),"
                + "is_active INT, practitioner_id VARCHAR(64), consent_signed INT, consent_signed_at VARCHAR(32),"
                + "consent_token VARCHAR(64), consent_token_expires VARCHAR(32), merged_into VARCHAR(64),"
                + "deleted_at VARCHAR(32), payload VARCHAR(10000), create_time TIMESTAMP, update_time TIMESTAMP)");
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.getTypeAliasRegistry().registerAlias("TcmPatient", TcmPatient.class);
        configuration.getTypeAliasRegistry().registerAlias("TcmAppointment", TcmAppointment.class);
        configuration.getTypeAliasRegistry().registerAlias("TcmConsultation", TcmConsultation.class);
        String resource = "mapper/hospital/TcmPatientMapper.xml";
        String xml;
        try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream); xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        new XMLMapperBuilder(new StringReader(xml), configuration, resource, configuration.getSqlFragments()).parse();
        for (String childMapper : new String[] {"TcmAppointmentMapper", "TcmConsultationMapper"}) {
            String childResource = "mapper/hospital/" + childMapper + ".xml";
            try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(childResource)) {
                assertNotNull(stream);
                new XMLMapperBuilder(stream, configuration, childResource, configuration.getSqlFragments()).parse();
            }
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
        session = factory.openSession(true);
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

    @Test void ownershipMigrationPreservesConcurrentDeliveryAndPaymentUpdatesIncludingDeletedRows() throws Exception {
        jdbc.execute("CREATE TABLE tcm_appointment (id VARCHAR(64), patient_id VARCHAR(64), payload VARCHAR(1000), status VARCHAR(32), update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE tcm_consultation (id VARCHAR(64), patient_id VARCHAR(64), payload VARCHAR(1000), status VARCHAR(32), deleted_at VARCHAR(32), update_time TIMESTAMP)");
        jdbc.update("INSERT INTO tcm_appointment(id,patient_id,payload,status) VALUES ('event','source','{}','booked'),('other-event','source','{}','cancelled'),('unrelated','unrelated','{}','booked')");
        jdbc.update("INSERT INTO tcm_consultation(id,patient_id,payload,status,deleted_at) VALUES ('paid','source','{}','draft',NULL),('deleted','source','{}','draft','2026-01-01'),('unrelated','unrelated','{}','draft',NULL)");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        Future<int[]> moving = null;
        try (java.sql.Connection writer = source.getConnection()) {
            writer.setAutoCommit(false);
            try (java.sql.Statement update = writer.createStatement()) {
                update.executeUpdate("UPDATE tcm_appointment SET payload='{\"confirmationEmailSentAt\":\"sent\"}',status='completed' WHERE id='event'");
                update.executeUpdate("UPDATE tcm_consultation SET payload='{\"paid\":true}',status='locked' WHERE id='paid'");
            }
            CountDownLatch started = new CountDownLatch(1);
            moving = worker.submit(() -> {
                try (SqlSession migration = factory.openSession(true)) {
                    started.countDown();
                    return new int[] {
                            migration.getMapper(TcmAppointmentMapper.class).reassignPatient("source", "keep"),
                            migration.getMapper(TcmConsultationMapper.class).reassignPatient("source", "keep")};
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<int[]> pending = moving;
            assertThrows(TimeoutException.class, () -> pending.get(100, TimeUnit.MILLISECONDS));
            writer.commit();
            assertArrayEquals(new int[] {2, 2}, moving.get(5, TimeUnit.SECONDS));
        } finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals("{\"confirmationEmailSentAt\":\"sent\"}", jdbc.queryForObject("SELECT payload FROM tcm_appointment WHERE id='event'", String.class));
        assertEquals("completed", jdbc.queryForObject("SELECT status FROM tcm_appointment WHERE id='event'", String.class));
        assertEquals("{\"paid\":true}", jdbc.queryForObject("SELECT payload FROM tcm_consultation WHERE id='paid'", String.class));
        assertEquals("locked", jdbc.queryForObject("SELECT status FROM tcm_consultation WHERE id='paid'", String.class));
        assertEquals("2026-01-01", jdbc.queryForObject("SELECT deleted_at FROM tcm_consultation WHERE id='deleted'", String.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tcm_appointment WHERE patient_id='keep'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tcm_consultation WHERE patient_id='keep'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tcm_appointment WHERE patient_id='keep' AND update_time IS NOT NULL", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tcm_consultation WHERE patient_id='keep' AND update_time IS NOT NULL", Integer.class));
        assertEquals("unrelated", jdbc.queryForObject("SELECT patient_id FROM tcm_appointment WHERE id='unrelated'", String.class));
        assertEquals("unrelated", jdbc.queryForObject("SELECT patient_id FROM tcm_consultation WHERE id='unrelated'", String.class));
    }
}
