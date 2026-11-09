package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.impl.TcmAppointmentServiceImpl;
import com.ruoyi.hospital.service.impl.TcmPatientServiceImpl;

/** Real intake endpoint/services and committed writes, with an explicit old ordinary-read
 * view to model MySQL RR. H2 itself is only used for locking/current persistence semantics. */
class TcmIntakeCurrentPatientRegressionTest {
    @Test void appointmentIntakePreservesSignatureCommittedAfterTheInitialReadView() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:intake_current_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE patients (id VARCHAR(64) PRIMARY KEY, body CLOB)");
        jdbc.execute("CREATE TABLE appointments (id VARCHAR(64) PRIMARY KEY, body CLOB)");
        TcmPatient oldReadView = new TcmPatient(); oldReadView.setId("patient"); oldReadView.setName("Synthetic Patient");
        oldReadView.setIsActive(1); oldReadView.setConsentSigned(0); oldReadView.setConsentToken("old-consent");
        oldReadView.setPayload("{}");
        TcmPatient current = JSON.parseObject(JSON.toJSONString(oldReadView), TcmPatient.class);
        current.setConsentSigned(1); current.setConsentToken(null);
        current.setPayload("{\"consentSignatureName\":\"Synthetic Signature\",\"consentPdfPath\":\"hospital-private/synthetic/signed.pdf\"}");
        jdbc.update("INSERT INTO patients VALUES (?,?)", current.getId(), JSON.toJSONString(current));
        TcmAppointment appointment = new TcmAppointment(); appointment.setId("appointment"); appointment.setPatientId("patient");
        appointment.setStatus("booked"); appointment.setIntakeToken("intake-token"); appointment.setIntakeSubmitted(0); appointment.setPayload("{}");
        jdbc.update("INSERT INTO appointments VALUES (?,?)", appointment.getId(), JSON.toJSONString(appointment));
        TcmPatientMapper patientMapper = mock(TcmPatientMapper.class);
        when(patientMapper.selectTcmPatientById("patient")).thenAnswer(call -> JSON.parseObject(JSON.toJSONString(oldReadView), TcmPatient.class));
        when(patientMapper.selectTcmPatientByIdForUpdate("patient")).thenAnswer(call -> JSON.parseObject(
                jdbc.queryForObject("SELECT body FROM patients WHERE id='patient' FOR UPDATE", String.class), TcmPatient.class));
        when(patientMapper.updateTcmPatient(any())).thenAnswer(call -> jdbc.update("UPDATE patients SET body=? WHERE id='patient'", JSON.toJSONString(call.getArgument(0))));
        TcmAppointmentMapper appointmentMapper = mock(TcmAppointmentMapper.class);
        when(appointmentMapper.selectTcmAppointmentByIntakeToken("intake-token")).thenAnswer(call -> JSON.parseObject(jdbc.queryForObject("SELECT body FROM appointments", String.class), TcmAppointment.class));
        when(appointmentMapper.selectTcmAppointmentById("appointment")).thenAnswer(call -> JSON.parseObject(jdbc.queryForObject("SELECT body FROM appointments", String.class), TcmAppointment.class));
        when(appointmentMapper.selectTcmAppointmentForUpdate("appointment")).thenAnswer(call -> JSON.parseObject(jdbc.queryForObject("SELECT body FROM appointments WHERE id='appointment' FOR UPDATE", String.class), TcmAppointment.class));
        when(appointmentMapper.updateNotificationIntake(any(), any())).thenReturn(1);
        when(appointmentMapper.updateTcmAppointment(any())).thenAnswer(call -> jdbc.update("UPDATE appointments SET body=? WHERE id='appointment'", JSON.toJSONString(call.getArgument(0))));
        TcmAppointmentServiceImpl booking = new TcmAppointmentServiceImpl();
        ReflectionTestUtils.setField(booking, "appointmentMapper", appointmentMapper);
        ReflectionTestUtils.setField(booking, "patientMapper", patientMapper);
        TcmPatientServiceImpl patients = new TcmPatientServiceImpl();
        ReflectionTestUtils.setField(patients, "tcmPatientMapper", patientMapper);
        TcmIntakeController target = new TcmIntakeController();
        ReflectionTestUtils.setField(target, "appointmentService", booking);
        ReflectionTestUtils.setField(target, "patientService", patients);
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source), new AnnotationTransactionAttributeSource()));
        TcmIntakeController controller = (TcmIntakeController) factory.getProxy();

        assertEquals(true, controller.submitIntakeForm("intake-token", Map.of("chiefComplaint", "Synthetic intake")).get("ok"));
        TcmPatient saved = JSON.parseObject(jdbc.queryForObject("SELECT body FROM patients", String.class), TcmPatient.class);
        JSONObject payload = JSONObject.parseObject(saved.getPayload());
        assertAll(
                () -> assertEquals(1, saved.getConsentSigned(), "The completed signature must survive the intake update"),
                () -> assertNull(saved.getConsentToken(), "Consumed consent token must not be resurrected"),
                () -> assertEquals("Synthetic Signature", payload.getString("consentSignatureName")),
                () -> assertEquals("hospital-private/synthetic/signed.pdf", payload.getString("consentPdfPath")),
                () -> assertTrue(saved.getPayload().contains("Synthetic intake")));
        TcmAppointment savedAppointment = JSON.parseObject(jdbc.queryForObject("SELECT body FROM appointments", String.class), TcmAppointment.class);
        assertEquals(1, savedAppointment.getIntakeSubmitted());
        assertNull(savedAppointment.getIntakeToken());
    }
}
