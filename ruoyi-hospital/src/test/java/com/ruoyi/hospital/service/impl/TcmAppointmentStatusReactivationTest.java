package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;

/** Real status service and committed H2 writes; no notifications or external systems. */
class TcmAppointmentStatusReactivationTest
{
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private TcmAppointmentServiceImpl service;

    @BeforeEach void setup()
    {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:status_reactivation_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE appointments (id VARCHAR(64) PRIMARY KEY, body VARCHAR(10000))");
        jdbc.execute("CREATE TABLE patients (id VARCHAR(64) PRIMARY KEY)");
        jdbc.update("INSERT INTO patients VALUES ('patient')");
        jdbc.update("INSERT INTO patients VALUES ('replacement-patient')");
        jdbc.update("INSERT INTO appointments VALUES (?, ?)", "old", JSON.toJSONString(appointment("old", "cancelled")));
        jdbc.update("INSERT INTO appointments VALUES (?, ?)", "replacement", JSON.toJSONString(appointment("replacement", "booked")));
        TcmPatientMapper patients = mock(TcmPatientMapper.class);
        when(patients.selectTcmPatientByIdForUpdate(anyString())).thenAnswer(call -> {
            String id = call.getArgument(0);
            jdbc.queryForObject("SELECT id FROM patients WHERE id=? FOR UPDATE", String.class, id);
            TcmPatient patient = new TcmPatient(); patient.setId(id); patient.setIsActive(1); return patient;
        });
        TcmAppointmentMapper appointments = mock(TcmAppointmentMapper.class);
        when(appointments.selectTcmAppointmentById(anyString())).thenAnswer(call -> current(call.getArgument(0)));
        when(appointments.selectTcmAppointmentForUpdate(anyString())).thenAnswer(call -> {
            jdbc.queryForObject("SELECT id FROM appointments WHERE id=? FOR UPDATE", String.class, (String) call.getArgument(0));
            return current(call.getArgument(0));
        });
        when(appointments.updateTcmAppointment(any())).thenAnswer(call -> {
            TcmAppointment updated = call.getArgument(0);
            return jdbc.update("UPDATE appointments SET body=? WHERE id=?", JSON.toJSONString(updated), updated.getId());
        });
        service = new TcmAppointmentServiceImpl();
        ReflectionTestUtils.setField(service, "appointmentMapper", appointments);
        ReflectionTestUtils.setField(service, "patientMapper", patients);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @ParameterizedTest @ValueSource(strings = {"booked", "confirmed"})
    void staleStatusRequestCannotRestoreACancelledAppointmentIntoAnOccupiedSlot(String requestedStatus)
    {
        // The staff page was opened before public cancellation; another patient has
        // since taken the same practitioner/room/time. This is the stale button request.
        ServiceException rejected = null;
        try { transaction.execute(status -> service.updateStatus("old", requestedStatus)); }
        catch (ServiceException error) { rejected = error; }
        assertEquals(1, rows().stream().filter(row -> "booked".equals(row.getStatus()) || "confirmed".equals(row.getStatus())).count(),
                "A cancelled appointment must not silently reclaim a slot already booked by another appointment");
        assertNotNull(rejected, "A stale confirmation must ask staff to refresh instead of reporting success");
        assertEquals("This appointment has been cancelled. Please create a new booking.", rejected.getMessage());
        assertEquals("cancelled", current("old").getStatus());
        assertEquals("booked", current("replacement").getStatus());
    }

    @Test void anExistingBookingCanStillBeConfirmed()
    {
        transaction.execute(status -> service.updateStatus("replacement", "confirmed"));
        assertEquals("confirmed", current("replacement").getStatus());
        assertEquals("cancelled", current("old").getStatus());
    }

    @Test void repeatingCancellationRemainsIdempotent()
    {
        transaction.execute(status -> service.updateStatus("old", "cancelled"));
        assertEquals("cancelled", current("old").getStatus());
        assertEquals("booked", current("replacement").getStatus());
    }

    private TcmAppointment appointment(String id, String status)
    {
        TcmAppointment appointment = new TcmAppointment(); appointment.setId(id);
        appointment.setPatientId("replacement".equals(id) ? "replacement-patient" : "patient");
        appointment.setPractitionerId("doctor"); appointment.setRoomId("room"); appointment.setServiceType("acupuncture");
        appointment.setStartTime("2026-11-16 09:00:00"); appointment.setEndTime("2026-11-16 09:30:00");
        appointment.setStatus(status); appointment.setPayload("{}"); return appointment;
    }

    private TcmAppointment current(String id)
    {
        return JSON.parseObject(jdbc.queryForObject("SELECT body FROM appointments WHERE id=?", String.class, id), TcmAppointment.class);
    }

    private List<TcmAppointment> rows()
    {
        return jdbc.query("SELECT body FROM appointments", (row, index) -> JSON.parseObject(row.getString(1), TcmAppointment.class));
    }
}
