package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.service.ITcmAppointmentNotificationService;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmPatientService;

@ExtendWith(MockitoExtension.class)
class TcmIntakeControllerTest
{
    @Mock
    private ITcmAppointmentService appointmentService;

    @Mock
    private ITcmPatientService patientService;

    @Mock
    private ITcmAppointmentNotificationService appointmentNotificationService;

    private TcmIntakeController controller;

    @BeforeEach
    void setUp()
    {
        controller = new TcmIntakeController();
        ReflectionTestUtils.setField(controller, "appointmentService", appointmentService);
        ReflectionTestUtils.setField(controller, "patientService", patientService);
        ReflectionTestUtils.setField(controller, "appointmentNotificationService", appointmentNotificationService);
    }

    @Test
    void cancelAppointment_shouldDelegateToNotificationService()
    {
        TcmAppointment appointment = new TcmAppointment();
        appointment.setId("appt-9");
        appointment.setStatus("cancelled");
        when(appointmentNotificationService.cancelByIntakeToken("intake-token", "patient_intake_form"))
                .thenReturn(appointment);

        Map<String, Object> result = controller.cancelAppointment("intake-token");

        assertEquals(Boolean.TRUE, result.get("ok"));
        assertEquals("appt-9", result.get("appointmentId"));
        assertEquals("cancelled", result.get("status"));
        verify(appointmentNotificationService).cancelByIntakeToken("intake-token", "patient_intake_form");
    }
}
