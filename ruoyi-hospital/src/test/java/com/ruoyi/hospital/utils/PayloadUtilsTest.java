package com.ruoyi.hospital.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;

class PayloadUtilsTest
{
    @Test
    void toPatient_shouldSyncPrimaryEmailFromEmailsArray()
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", "old@example.com");
        body.put("emails", Arrays.asList("new@example.com"));

        TcmPatient patient = PayloadUtils.toPatient(body);

        assertEquals("new@example.com", patient.getEmail());
    }

    @Test
    void flattenConsultation_shouldNotLetPayloadOverrideConsultDate()
    {
        TcmConsultation consultation = new TcmConsultation();
        consultation.setId("consult-1");
        consultation.setConsultationId("ORD-NEW");
        consultation.setPatientId("patient-1");
        consultation.setPractitionerId("doctor-1");
        consultation.setConsultDate("2026-05-21");
        consultation.setStatus("draft");
        consultation.setPayload("{\"date\":\"2026-05-11\",\"consultDate\":\"2026-05-10\",\"chiefComplaint\":\"Back Pain\"}");

        Map<String, Object> flattened = PayloadUtils.flatten(consultation);

        assertEquals("2026-05-21", flattened.get("date"));
        assertEquals("Back Pain", flattened.get("chiefComplaint"));
    }
}
