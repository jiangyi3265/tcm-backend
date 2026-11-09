package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.mapper.*;
import com.ruoyi.hospital.service.ITcmPatientFileService;

/** Service-level identity gates and parent-before-child lock ordering. */
class TcmPatientLifecycleWriteTest {
    private final TcmPatientMapper patients = mock(TcmPatientMapper.class);
    private final TcmAppointmentMapper appointments = mock(TcmAppointmentMapper.class);
    private final TcmConsultationMapper consultations = mock(TcmConsultationMapper.class);
    private final TcmAppointmentServiceImpl booking = new TcmAppointmentServiceImpl();
    private final TcmConsultationServiceImpl clinical = new TcmConsultationServiceImpl();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(booking, "appointmentMapper", appointments);
        ReflectionTestUtils.setField(booking, "patientMapper", patients);
        ReflectionTestUtils.setField(clinical, "consultationMapper", consultations);
        ReflectionTestUtils.setField(clinical, "patientMapper", patients);
        ReflectionTestUtils.setField(clinical, "patientFileService", mock(ITcmPatientFileService.class));
        when(appointments.insertTcmAppointment(any())).thenReturn(1);
        when(appointments.updateTcmAppointment(any())).thenReturn(1);
        when(consultations.insertTcmConsultation(any())).thenReturn(1);
        when(consultations.updateTcmConsultation(any())).thenReturn(1);
    }
    static TcmPatient patient(String id, String state) {
        if ("missing".equals(state)) return null;
        TcmPatient p = new TcmPatient(); p.setId(id); p.setIsActive(1); p.setPayload("{}");
        if ("inactive".equals(state)) p.setIsActive(0);
        if ("unknown".equals(state)) p.setIsActive(null);
        if ("deleted".equals(state)) p.setDeletedAt("2026-11-01 09:00:00");
        if ("merged".equals(state)) p.setMergedInto("other");
        return p;
    }
    static TcmAppointment appointment(String patientId) {
        TcmAppointment a = new TcmAppointment(); a.setId("a"); a.setPatientId(patientId);
        a.setServiceType("time_block"); a.setPractitionerId("doctor"); a.setStatus("booked");
        a.setStartTime("2026-12-01 09:00:00"); a.setEndTime("2026-12-01 10:00:00"); a.setPayload("{}");
        return a;
    }
    static TcmConsultation consultation(String patientId) {
        TcmConsultation c = new TcmConsultation(); c.setId("c"); c.setConsultationId("C-1");
        c.setPatientId(patientId); c.setStatus("draft"); c.setVersion(1); c.setPayload("{}");
        return c;
    }
    @ParameterizedTest @ValueSource(strings={"inactive","unknown","deleted","merged","missing"})
    void newRecordsRejectRetiredOrMissingPatient(String state) {
        when(patients.selectTcmPatientByIdForUpdate("target")).thenReturn(patient("target", state));
        assertThrows(ServiceException.class, () -> booking.insertTcmAppointment(appointment("target")));
        assertThrows(ServiceException.class, () -> clinical.insertTcmConsultation(consultation("target")));
        verify(appointments, never()).insertTcmAppointment(any());
        verify(consultations, never()).insertTcmConsultation(any());
    }
    @ParameterizedTest @ValueSource(strings={"inactive","unknown","deleted","merged","missing"})
    void retargetRejectsRetiredOrMissingPatient(String state) {
        when(appointments.selectTcmAppointmentById("a")).thenReturn(appointment("old"));
        when(appointments.selectTcmAppointmentForUpdate("a")).thenReturn(appointment("old"));
        when(consultations.selectTcmConsultationById("c")).thenReturn(consultation("old"));
        when(consultations.selectTcmConsultationForUpdate("c")).thenReturn(consultation("old"));
        when(patients.selectTcmPatientByIdForUpdate("target")).thenReturn(patient("target", state));
        assertThrows(ServiceException.class, () -> booking.updateTcmAppointment(appointment("target")));
        assertThrows(ServiceException.class, () -> clinical.updateTcmConsultation(consultation("target"), "actor"));
        verify(appointments, never()).updateTcmAppointment(any());
        verify(consultations, never()).updateTcmConsultation(any());
    }
    @Test void retargetLocksBothParentsInMergeOrderBeforeChildren() {
        when(appointments.selectTcmAppointmentById("a")).thenReturn(appointment("z"));
        when(appointments.selectTcmAppointmentForUpdate("a")).thenReturn(appointment("z"));
        when(consultations.selectTcmConsultationById("c")).thenReturn(consultation("z"));
        when(consultations.selectTcmConsultationForUpdate("c")).thenReturn(consultation("z"));
        when(patients.selectTcmPatientByIdForUpdate("b")).thenReturn(patient("b", "active"));
        assertEquals(1, booking.updateTcmAppointment(appointment("b")));
        assertEquals(1, clinical.updateTcmConsultation(consultation("b"), "actor"));
        InOrder order = inOrder(patients, appointments, consultations);
        order.verify(patients).selectTcmPatientByIdForUpdate("b");
        order.verify(patients).selectTcmPatientByIdForUpdate("z");
        order.verify(appointments).selectTcmAppointmentForUpdate("a");
        order.verify(patients).selectTcmPatientByIdForUpdate("b");
        order.verify(patients).selectTcmPatientByIdForUpdate("z");
        order.verify(consultations).selectTcmConsultationForUpdate("c");
    }
    @Test void changedChildOwnershipIsRejectedAfterLocksInsteadOfLockingAThirdParent() {
        when(appointments.selectTcmAppointmentById("a")).thenReturn(appointment("old"));
        when(appointments.selectTcmAppointmentForUpdate("a")).thenReturn(appointment("merged-target"));
        when(consultations.selectTcmConsultationById("c")).thenReturn(consultation("old"));
        when(consultations.selectTcmConsultationForUpdate("c")).thenReturn(consultation("merged-target"));
        assertThrows(ServiceException.class, () -> booking.updateStatus("a", "cancelled"));
        assertThrows(ServiceException.class, () -> clinical.updateTcmConsultation(consultation("old"), "actor"));
        verify(patients, never()).selectTcmPatientByIdForUpdate("merged-target");
        verify(appointments, never()).updateTcmAppointment(any());
        verify(consultations, never()).updateTcmConsultation(any());
    }
    @ParameterizedTest @ValueSource(strings={"inactive","deleted","merged"})
    void sameHistoricalPatientCanStillEditAndCancel(String state) {
        when(patients.selectTcmPatientByIdForUpdate("old")).thenReturn(patient("old", state));
        when(appointments.selectTcmAppointmentById("a")).thenReturn(appointment("old"));
        when(appointments.selectTcmAppointmentForUpdate("a")).thenReturn(appointment("old"));
        when(consultations.selectTcmConsultationById("c")).thenReturn(consultation("old"));
        when(consultations.selectTcmConsultationForUpdate("c")).thenReturn(consultation("old"));
        assertEquals(1, booking.updateTcmAppointment(appointment("old")));
        assertNotNull(booking.updateStatus("a", "cancelled"));
        assertEquals(1, clinical.updateTcmConsultation(consultation("old"), "actor"));
    }
    @Test void patientFreeTimeBlockAcceptsBlankOrNullOnCreateAndUpdate() {
        assertEquals(1, booking.insertTcmAppointment(appointment(null)));
        assertEquals(1, booking.insertTcmAppointment(appointment("")));
        when(appointments.selectTcmAppointmentById("a")).thenReturn(appointment(null));
        when(appointments.selectTcmAppointmentForUpdate("a")).thenReturn(appointment(null));
        assertEquals(1, booking.updateTcmAppointment(appointment("")));
        verifyNoInteractions(patients);
    }
    @Test void primaryPractitionerUpdateUsesCurrentProfileRatherThanEarlierReadView() {
        // Explicitly model a stale MySQL RR read-view; H2 does not establish that semantic.
        TcmPatient stale = patient("p", "active"); stale.setConsentSigned(0); stale.setPayload("{\"version\":\"old\"}");
        TcmPatient current = patient("p", "active"); current.setConsentSigned(1); current.setPayload("{\"version\":\"signed\"}");
        when(patients.selectTcmPatientById("p")).thenReturn(stale);
        when(patients.selectTcmPatientByIdForUpdate("p")).thenReturn(current);
        when(consultations.selectTcmConsultationById("c")).thenReturn(consultation("p"));
        when(consultations.selectTcmConsultationForUpdate("c")).thenReturn(consultation("p"));
        TcmConsultation incoming = consultation("p"); incoming.setPractitionerId("doctor");
        assertEquals(1, clinical.updateTcmConsultation(incoming, "actor"));
        verify(patients).updateTcmPatient(argThat(p -> p.getConsentSigned() == 1 && p.getPayload().contains("signed")));
        verify(patients, never()).selectTcmPatientById("p");
    }
}
