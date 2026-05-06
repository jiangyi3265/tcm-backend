package com.ruoyi.hospital.service;

import java.util.Map;

public interface ITcmStripePaymentService
{
    Map<String, Object> createCheckoutSession(String consultationId);

    Map<String, Object> handleWebhook(String payload, String signatureHeader);
}
