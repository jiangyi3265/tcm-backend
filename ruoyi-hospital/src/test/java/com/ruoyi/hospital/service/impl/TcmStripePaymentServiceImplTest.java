package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmEmailService;
import com.ruoyi.hospital.service.ITcmSettingsService;

@ExtendWith(MockitoExtension.class)
class TcmStripePaymentServiceImplTest
{
    @Mock
    private ITcmConsultationService consultationService;

    @Mock
    private TcmPatientMapper patientMapper;

    @Mock
    private ITcmEmailService emailService;

    @Mock
    private ITcmSettingsService settingsService;

    private TcmStripePaymentServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmStripePaymentServiceImpl();
        ReflectionTestUtils.setField(service, "consultationService", consultationService);
        ReflectionTestUtils.setField(service, "patientMapper", patientMapper);
        ReflectionTestUtils.setField(service, "emailService", emailService);
        ReflectionTestUtils.setField(service, "settingsService", settingsService);
        when(settingsService.getStripeWebhookSecret()).thenReturn("");
    }

    @Test
    void handleWebhook_shouldRecordTerminalPaymentIntentSucceeded()
    {
        TcmConsultation existing = new TcmConsultation();
        existing.setId("consult-1");
        existing.setPayload("{\"paymentRecords\":[]}");
        TcmConsultation recorded = new TcmConsultation();
        recorded.setId("consult-1");
        recorded.setPayload("{}");

        when(consultationService.selectTcmConsultationById("consult-1")).thenReturn(existing);
        when(consultationService.recordProviderPayment(eq("consult-1"), eq("stripe_terminal"), anyMap()))
                .thenReturn(recorded);
        when(settingsService.getStripeTerminalReaderId()).thenReturn("tmr_reader");

        String payload = "{"
                + "\"id\":\"evt_pi\","
                + "\"type\":\"payment_intent.succeeded\","
                + "\"livemode\":true,"
                + "\"data\":{\"object\":{"
                + "\"id\":\"pi_123\","
                + "\"status\":\"succeeded\","
                + "\"amount\":1234,"
                + "\"amount_received\":1234,"
                + "\"currency\":\"cad\","
                + "\"livemode\":true,"
                + "\"metadata\":{\"consultationId\":\"consult-1\"}"
                + "}}"
                + "}";

        Map<String, Object> result = service.handleWebhook(payload, "");

        assertTrue((Boolean) result.get("received"));
        assertTrue((Boolean) result.get("processed"));
        assertEquals("payment_intent.succeeded", result.get("type"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paymentInfoCaptor = ArgumentCaptor.forClass(Map.class);
        verify(consultationService).recordProviderPayment(
                eq("consult-1"),
                eq("stripe_terminal"),
                paymentInfoCaptor.capture());
        Map<String, Object> paymentInfo = paymentInfoCaptor.getValue();
        assertEquals("stripe_terminal", paymentInfo.get("provider"));
        assertEquals("bankcard", paymentInfo.get("paymentMethod"));
        assertEquals(new BigDecimal("12.34"), paymentInfo.get("amount"));
        assertEquals("CAD", paymentInfo.get("currency"));
        assertEquals("pi_123", paymentInfo.get("stripePaymentIntentId"));
        assertEquals("tmr_reader", paymentInfo.get("stripeReaderId"));
    }

    @Test
    void handleWebhook_shouldAcknowledgeReaderEventsWithoutRecordingPayment()
    {
        String payload = "{"
                + "\"id\":\"evt_reader\","
                + "\"type\":\"terminal.reader.action_failed\","
                + "\"data\":{\"object\":{\"id\":\"tmr_reader\"}}"
                + "}";

        Map<String, Object> result = service.handleWebhook(payload, "");

        assertTrue((Boolean) result.get("received"));
        assertFalse((Boolean) result.get("processed"));
        assertEquals("terminal.reader.action_failed", result.get("type"));
        verify(consultationService, never()).recordProviderPayment(eq("consult-1"), eq("stripe_terminal"), anyMap());
    }

    @Test
    void handleWebhook_shouldAcceptAnyMatchingV1Signature()
    {
        String payload = "{"
                + "\"id\":\"evt_reader\","
                + "\"type\":\"terminal.reader.action_failed\","
                + "\"data\":{\"object\":{\"id\":\"tmr_reader\"}}"
                + "}";
        String webhookSecret = "whsec_test";
        long timestamp = System.currentTimeMillis() / 1000L;
        String signature = ReflectionTestUtils.invokeMethod(
                service,
                "hmacSha256",
                timestamp + "." + payload,
                webhookSecret);
        when(settingsService.getStripeWebhookSecret()).thenReturn(webhookSecret);

        Map<String, Object> result = service.handleWebhook(
                payload,
                "t=" + timestamp + ",v1=" + signature + ",v1=invalid_signature");

        assertTrue((Boolean) result.get("received"));
        assertFalse((Boolean) result.get("processed"));
        assertEquals("terminal.reader.action_failed", result.get("type"));
    }
}
