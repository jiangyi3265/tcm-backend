package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.service.ITcmAppointmentNotificationService;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmPatientService;

/** Exercises the actual public booking endpoint with isolated persisted patient/appointment state. */
class TcmPublicBookingIdentityTest
{
    private final Map<String, TcmPatient> patients = new ConcurrentHashMap<>();
    private final Map<String, TcmAppointment> appointments = new ConcurrentHashMap<>();
    private final ITcmPatientService patientService = mock(ITcmPatientService.class);
    private final ITcmAppointmentService appointmentService = mock(ITcmAppointmentService.class);
    private final ITcmAppointmentNotificationService notifications = mock(ITcmAppointmentNotificationService.class);
    private TcmPublicBookingController controller;

    @BeforeEach
    void setUp()
    {
        controller = new TcmPublicBookingController();
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        ReflectionTestUtils.setField(controller, "appointmentService", appointmentService);
        ReflectionTestUtils.setField(controller, "appointmentNotificationService", notifications);
        when(patientService.selectTcmPatientList(any())).thenAnswer(invocation -> {
            List<TcmPatient> result = new ArrayList<>();
            for (TcmPatient patient : patients.values()) result.add(copy(patient));
            return result;
        });
        when(patientService.selectTcmPatientById(anyString())).thenAnswer(invocation -> copy(patients.get(invocation.getArgument(0))));
        when(patientService.insertTcmPatient(any())).thenAnswer(invocation -> {
            TcmPatient patient = invocation.getArgument(0);
            patients.put(patient.getId(), copy(patient));
            return 1;
        });
        when(patientService.updateTcmPatient(any())).thenAnswer(invocation -> {
            TcmPatient patient = invocation.getArgument(0);
            patients.put(patient.getId(), copy(patient));
            return 1;
        });
        when(appointmentService.insertTcmAppointment(any())).thenAnswer(invocation -> {
            TcmAppointment appointment = invocation.getArgument(0);
            appointments.put(appointment.getId(), appointment);
            return 1;
        });
        when(appointmentService.selectTcmAppointmentById(anyString())).thenAnswer(invocation -> appointments.get(invocation.getArgument(0)));
    }

    @Test
    void sharedFamilyEmailAndPhoneKeepSeparateNamesHistoriesAndConsent()
    {
        String alice = book("alice-first", "Alice", "Lee", "family@example.test", "5550100");
        TcmPatient original = patients.get(alice);
        original.setConsentSigned(1);
        original.setConsentToken("alice-only-token");
        original.setPayload("{\"medicalHistory\":\"Alice only\",\"emails\":[\"family@example.test\"]}");
        String bob = book("bob-first", "Bob", "Lee", "family@example.test", "5550100");
        String child = book("child-first", "Charlie", "Lee", "family@example.test", "5550100");
        String aliceAgain = book("alice-repeat", "Alice", "Lee", "family@example.test", "5550100");

        assertNotEquals(alice, bob);
        assertNotEquals(alice, child);
        assertNotEquals(bob, child);
        assertEquals(alice, aliceAgain);
        assertEquals(3, patients.size());
        assertEquals("Lee Alice", patients.get(alice).getName());
        assertEquals("Lee Bob", patients.get(bob).getName());
        assertEquals("Lee Charlie", patients.get(child).getName());
        assertEquals(1, patients.get(alice).getConsentSigned());
        assertEquals("alice-only-token", patients.get(alice).getConsentToken());
        assertTrue(patients.get(alice).getPayload().contains("Alice only"));
        assertEquals(0, patients.get(bob).getConsentSigned());
        assertFalse(patients.get(bob).getPayload().contains("Alice only"));
        assertEquals(alice, appointments.get("alice-first").getPatientId());
        assertEquals(bob, appointments.get("bob-first").getPatientId());
        verify(notifications).handleAppointmentCreated(appointments.get("bob-first"));
    }

    @Test
    void matchingIgnoresCaseAndRepeatedWhitespaceButKeepsNamePartsDistinct()
    {
        String first = book("first", "Mary Jane", "Lee", "Family@Example.test", "5550100");
        assertEquals(first, book("repeat", " mary\u00a0 jane ", " LEE ", " family@example.test ", "5550101"));
        assertEquals(1, patients.size());
        assertEquals("5550101", patients.get(first).getPhone());
        assertNotEquals(first, book("different", "Mary", "Jane Lee", "family@example.test", "5550101"));
    }

    @Test
    void sharedPhoneAndNameDoNotOverrideDifferentSuppliedEmails()
    {
        String first = book("first", "Alex", "Lee", "one@example.test", "5550100");
        assertNotEquals(first, book("second", "Alex", "Lee", "two@example.test", "5550100"));
        assertEquals("one@example.test", patients.get(first).getEmail());
    }

    @Test
    void missingEmailCannotHijackAnExistingEmailIdentityThroughPhone()
    {
        String first = book("first", "Alex", "Lee", "one@example.test", "5550100");
        assertNotEquals(first, book("second", "Alex", "Lee", null, "5550100"));
    }

    @Test
    void legacyPhoneOnlyBookingsStillReuseTheirOwnRecord()
    {
        String first = book("first", "Alex", "Lee", null, "5550100");
        assertEquals(first, book("second", "Alex", "Lee", null, "5550100"));
        assertNotEquals(first, book("third", "Bob", "Lee", null, "5550100"));
    }

    @Test
    void legacyFullNameOnlyImportsMatchBothHistoricalDisplayOrders()
    {
        for (String displayName : Arrays.asList("Lee Alice", "Alice Lee"))
        {
            patients.clear();
            TcmPatient patient = new TcmPatient();
            patient.setId("legacy");
            patient.setName(displayName);
            patient.setEmail("family@example.test");
            patient.setIsActive(1);
            patient.setPractitionerId("doctor-1");
            patients.put(patient.getId(), patient);
            assertEquals("legacy", book(displayName, "Alice", "Lee", "family@example.test", "5550100"));
            assertEquals(1, patients.size());
        }
    }

    @Test
    void splitNameFieldsTakePrecedenceOverAStaleDisplayName()
    {
        String bob = book("first", "Bob", "Lee", "family@example.test", "5550100");
        patients.get(bob).setName("Lee Alice");
        assertNotEquals(bob, book("second", "Alice", "Lee", "family@example.test", "5550100"));
        assertEquals("Bob", patients.get(bob).getFirstName());
    }

    @Test
    void archivedMergedAndInactiveRecordsAreNotResurrected()
    {
        for (String state : Arrays.asList("deleted", "merged", "inactive"))
        {
            patients.clear();
            String previous = book(state, "Alice", "Lee", "family@example.test", "5550100");
            TcmPatient patient = patients.get(previous);
            if (state.equals("deleted")) patient.setDeletedAt("2026-01-01 00:00:00");
            if (state.equals("merged")) patient.setMergedInto("other-record");
            if (state.equals("inactive")) patient.setIsActive(0);
            assertNotEquals(previous, book(state + "-new", "Alice", "Lee", "family@example.test", "5550100"));
        }
    }

    @Test
    void ambiguousHistoricalProfilesAreNotChosenByListOrder()
    {
        String first = book("first", "Alice", "Lee", "family@example.test", "5550100");
        TcmPatient duplicate = copy(patients.get(first));
        duplicate.setId("historical-duplicate");
        duplicate.setPayload("{\"medicalHistory\":\"Separate history\"}");
        patients.put(duplicate.getId(), duplicate);
        String booked = book("new", "Alice", "Lee", "family@example.test", "5550100");
        assertNotEquals(first, booked);
        assertNotEquals(duplicate.getId(), booked);
        assertEquals(3, patients.size());
        assertEquals("{\"medicalHistory\":\"Separate history\"}", patients.get(duplicate.getId()).getPayload());
    }

    @Test
    void simultaneousSameIdentityBookingsCreateOnePatient() throws Exception
    {
        // Delay persistence so an unguarded lookup/insert reliably overlaps.
        doAnswer(invocation -> {
            Thread.sleep(40);
            TcmPatient patient = invocation.getArgument(0);
            patients.put(patient.getId(), copy(patient));
            return 1;
        }).when(patientService).insertTcmPatient(any());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        try
        {
            for (int i = 0; i < 8; i++)
            {
                final String id = "parallel-" + i;
                results.add(executor.submit(() -> {
                    start.await();
                    return book(id, "Alice", "Lee", "family@example.test", "5550100");
                }));
            }
            start.countDown();
            String patientId = results.get(0).get(5, TimeUnit.SECONDS);
            for (Future<String> result : results) assertEquals(patientId, result.get(5, TimeUnit.SECONDS));
            assertEquals(1, patients.size());
            assertEquals(8, appointments.size());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private String book(String id, String first, String last, String email, String phone)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("firstName", first);
        body.put("lastName", last);
        body.put("email", email);
        body.put("phone", phone);
        body.put("practitionerId", "doctor-1");
        body.put("serviceType", "acupuncture");
        body.put("startTime", "2026-11-02 09:00:00");
        body.put("endTime", "2026-11-02 09:30:00");
        return (String) controller.create(body).get("patientId");
    }

    private TcmPatient copy(TcmPatient patient)
    {
        return patient == null ? null : JSON.parseObject(JSON.toJSONString(patient), TcmPatient.class);
    }
}
