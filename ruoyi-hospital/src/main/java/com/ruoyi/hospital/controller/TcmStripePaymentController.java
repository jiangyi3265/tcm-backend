package com.ruoyi.hospital.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.hospital.service.ITcmStripePaymentService;

@RestController
@RequestMapping("/api/stripe")
public class TcmStripePaymentController
{
    @Autowired
    private ITcmStripePaymentService stripePaymentService;

    @PreAuthorize("@ss.hasAnyRoles('admin,cashier,practitioner')")
    @PostMapping("/checkout-sessions")
    public Map<String, Object> createCheckoutSession(@RequestBody Map<String, Object> body)
    {
        return stripePaymentService.createCheckoutSession(String.valueOf(body.get("consultationId")));
    }

    @PostMapping("/webhook")
    public Map<String, Object> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader)
    {
        return stripePaymentService.handleWebhook(payload, signatureHeader);
    }
}
