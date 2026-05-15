package com.ruoyi.hospital.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
}
