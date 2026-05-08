package com.ruoyi.hospital.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmEmailService;
import com.ruoyi.hospital.service.ITcmStripePaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class TcmStripePaymentServiceImpl implements ITcmStripePaymentService
{
    private static final Logger log = LoggerFactory.getLogger(TcmStripePaymentServiceImpl.class);
    private static final String STRIPE_API_VERSION = "2026-02-25.clover";

    @Autowired
    private ITcmConsultationService consultationService;

    @Autowired
    private TcmPatientMapper patientMapper;

    @Autowired
    private ITcmEmailService emailService;

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @Value("${public.app-base-url:${PUBLIC_APP_BASE_URL:http://127.0.0.1:5173}}")
    private String publicAppBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, Object> createCheckoutSession(String consultationId)
    {
        if (StringUtils.isBlank(stripeSecretKey))
        {
            throw new ServiceException("Stripe secret key is not configured");
        }
        TcmConsultation consultation = consultationService.selectTcmConsultationById(consultationId);
        if (consultation == null)
        {
            throw new ServiceException("consultation not found");
        }
        if ("draft".equals(consultation.getStatus()))
        {
            throw new ServiceException("草稿问诊不能创建 Stripe 支付");
        }
        JSONObject payload = parsePayload(consultation.getPayload());
        BigDecimal outstanding = getOutstandingAmount(payload);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("当前没有可收款金额");
        }

        String currency = defaultText(payload.getString("currency"), "CAD").toLowerCase();
        long unitAmount = outstanding.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        TcmPatient patient = patientMapper.selectTcmPatientById(consultation.getPatientId());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", buildReturnUrl("stripe=success&session_id={CHECKOUT_SESSION_ID}"));
        form.add("cancel_url", buildReturnUrl("stripe=cancelled"));
        form.add("client_reference_id", consultation.getId());
        form.add("metadata[consultationId]", consultation.getId());
        form.add("metadata[consultationNo]", defaultText(consultation.getConsultationId(), consultation.getId()));
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", currency);
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(unitAmount));
        form.add("line_items[0][price_data][product_data][name]", "Invoice " + defaultText(consultation.getConsultationId(), consultation.getId()));
        if (patient != null && StringUtils.isNotBlank(patient.getEmail()))
        {
            form.add("customer_email", patient.getEmail().trim());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(stripeSecretKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add("Stripe-Version", STRIPE_API_VERSION);
        JSONObject response = restTemplate.postForObject(
                "https://api.stripe.com/v1/checkout/sessions",
                new HttpEntity<>(form, headers),
                JSONObject.class);
        if (response == null || StringUtils.isBlank(response.getString("url")))
        {
            throw new ServiceException("Stripe checkout session was not returned");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", response.getString("id"));
        result.put("url", response.getString("url"));
        return result;
    }

    @Override
    public Map<String, Object> handleWebhook(String payload, String signatureHeader)
    {
        if (StringUtils.isNotBlank(stripeWebhookSecret))
        {
            verifySignature(payload, signatureHeader);
        }
        JSONObject event = parsePayload(payload);
        String type = event.getString("type");
        if (!"checkout.session.completed".equals(type))
        {
            return ok(false, type);
        }
        JSONObject data = event.getJSONObject("data");
        JSONObject session = data != null ? data.getJSONObject("object") : null;
        if (session == null)
        {
            throw new ServiceException("Invalid Stripe webhook payload");
        }
        String consultationId = null;
        JSONObject metadata = session.getJSONObject("metadata");
        if (metadata != null)
        {
            consultationId = metadata.getString("consultationId");
        }
        if (StringUtils.isBlank(consultationId))
        {
            consultationId = session.getString("client_reference_id");
        }
        if (StringUtils.isBlank(consultationId))
        {
            throw new ServiceException("Stripe session missing consultationId");
        }

        Map<String, Object> paymentInfo = new HashMap<>();
        paymentInfo.put("provider", "stripe");
        paymentInfo.put("paymentMethod", "card");
        paymentInfo.put("amount", BigDecimal.valueOf(session.getLongValue("amount_total")).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        paymentInfo.put("currency", StringUtils.defaultIfBlank(session.getString("currency"), "CAD").toUpperCase());
        paymentInfo.put("stripeSessionId", session.getString("id"));
        paymentInfo.put("stripePaymentIntentId", session.getString("payment_intent"));
        paymentInfo.put("stripeEventId", event.getString("id"));
        paymentInfo.put("providerStatus", session.getString("payment_status"));
        paymentInfo.put("livemode", event.getBooleanValue("livemode"));
        boolean duplicatePayment = hasStripePaymentRecord(
                consultationId,
                session.getString("id"),
                session.getString("payment_intent"));
        TcmConsultation recorded = consultationService.recordProviderPayment(consultationId, "stripe", paymentInfo);
        if (!duplicatePayment)
        {
            sendInvoiceEmail(recorded, paymentInfo);
        }
        return ok(true, type);
    }

    private boolean hasStripePaymentRecord(String consultationId, String sessionId, String paymentIntentId)
    {
        TcmConsultation consultation = consultationService.selectTcmConsultationById(consultationId);
        if (consultation == null)
        {
            return false;
        }
        JSONObject payload = parsePayload(consultation.getPayload());
        JSONArray records = payload.getJSONArray("paymentRecords");
        if (records == null)
        {
            return false;
        }
        for (int i = 0; i < records.size(); i++)
        {
            JSONObject record = records.getJSONObject(i);
            if (record == null) continue;
            if ((StringUtils.isNotBlank(sessionId) && sessionId.equals(record.getString("stripeSessionId")))
                    || (StringUtils.isNotBlank(paymentIntentId) && paymentIntentId.equals(record.getString("stripePaymentIntentId"))))
            {
                return true;
            }
        }
        return false;
    }

    private void sendInvoiceEmail(TcmConsultation consultation, Map<String, Object> paymentInfo)
    {
        try
        {
            if (consultation == null || StringUtils.isBlank(consultation.getPatientId()))
            {
                return;
            }
            TcmPatient patient = patientMapper.selectTcmPatientById(consultation.getPatientId());
            if (patient == null || StringUtils.isBlank(patient.getEmail()))
            {
                return;
            }
            JSONObject payload = parsePayload(consultation.getPayload());
            String clinicName = defaultText(payload.getString("clinicName"), "TCM Clinic");
            String consultationNo = defaultText(consultation.getConsultationId(), consultation.getId());
            String consultationDate = defaultText(consultation.getConsultDate(), "");
            String currency = defaultText(stringValue(paymentInfo.get("currency")), defaultText(payload.getString("currency"), "CAD"));
            BigDecimal amountValue = toBigDecimal(paymentInfo.get("amount"));
            if (amountValue.compareTo(BigDecimal.ZERO) <= 0)
            {
                amountValue = toBigDecimal(payload.get("totalAmount"));
            }
            String amount = currency.toUpperCase() + " " + amountValue.setScale(2, RoundingMode.HALF_UP).toPlainString();
            String invoiceLink = defaultText(payload.getString("invoicePdfUrl"), "");

            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("clinicName", clinicName);
            variables.put("patientName", defaultText(patient.getName(), "病人"));
            variables.put("patientEmail", defaultText(patient.getEmail(), ""));
            variables.put("consultationId", consultationNo);
            variables.put("consultationDate", consultationDate);
            variables.put("amount", amount);
            variables.put("invoiceLink", invoiceLink);

            emailService.sendTemplateAndLog(
                    patient.getEmail(),
                    "invoice",
                    variables,
                    clinicName + "｜发票",
                    buildInvoiceFallbackBody(variables),
                    "invoice");
        }
        catch (Exception e)
        {
            log.warn("Stripe付款后发票邮件发送失败: consultationId={}, error={}",
                    consultation != null ? consultation.getId() : "", e.getMessage(), e);
        }
    }

    private String buildInvoiceFallbackBody(Map<String, Object> variables)
    {
        return "您好 " + stringValue(variables.get("patientName")) + "，您的发票已生成。\n\n"
                + "问诊编号：" + stringValue(variables.get("consultationId")) + "\n"
                + "日期：" + stringValue(variables.get("consultationDate")) + "\n"
                + "金额：" + stringValue(variables.get("amount")) + "\n"
                + "发票链接：" + stringValue(variables.get("invoiceLink")) + "\n\n"
                + "感谢您的到访。";
    }

    private String buildReturnUrl(String query)
    {
        String base = publicAppBaseUrl;
        if (StringUtils.isBlank(base))
        {
            base = "http://127.0.0.1:5173";
        }
        if (!base.endsWith("/"))
        {
            base += "/";
        }
        return base + "cashier?" + query;
    }

    private BigDecimal getOutstandingAmount(JSONObject payload)
    {
        BigDecimal total = toBigDecimal(payload.get("totalAmount"));
        BigDecimal paid = BigDecimal.ZERO;
        JSONArray records = payload.getJSONArray("paymentRecords");
        if (records != null)
        {
            for (int i = 0; i < records.size(); i++)
            {
                JSONObject record = records.getJSONObject(i);
                if (record != null)
                {
                    paid = paid.add(toBigDecimal(record.get("amount")));
                }
            }
        }
        BigDecimal outstanding = total.subtract(paid);
        return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
    }

    private void verifySignature(String payload, String signatureHeader)
    {
        if (StringUtils.isBlank(signatureHeader))
        {
            throw new ServiceException("Missing Stripe-Signature");
        }
        String timestamp = null;
        String signature = null;
        for (String part : signatureHeader.split(","))
        {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) continue;
            if ("t".equals(pair[0])) timestamp = pair[1];
            if ("v1".equals(pair[0])) signature = pair[1];
        }
        if (StringUtils.isBlank(timestamp) || StringUtils.isBlank(signature))
        {
            throw new ServiceException("Invalid Stripe-Signature");
        }
        long signedAt = Long.parseLong(timestamp);
        if (Math.abs(Instant.now().getEpochSecond() - signedAt) > 300)
        {
            throw new ServiceException("Expired Stripe webhook signature");
        }
        String expected = hmacSha256(timestamp + "." + payload, stripeWebhookSecret);
        if (!constantTimeEquals(expected, signature))
        {
            throw new ServiceException("Invalid Stripe webhook signature");
        }
    }

    private String hmacSha256(String value, String secret)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new ServiceException("Stripe signature verification failed");
        }
    }

    private boolean constantTimeEquals(String a, String b)
    {
        if (a == null || b == null || a.length() != b.length())
        {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++)
        {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private JSONObject parsePayload(String value)
    {
        if (StringUtils.isNotBlank(value))
        {
            try
            {
                return JSON.parseObject(value);
            }
            catch (Exception ignored) {}
        }
        return new JSONObject();
    }

    private BigDecimal toBigDecimal(Object value)
    {
        if (value == null) return BigDecimal.ZERO;
        try
        {
            return new BigDecimal(String.valueOf(value));
        }
        catch (Exception e)
        {
            return BigDecimal.ZERO;
        }
    }

    private String defaultText(String value, String fallback)
    {
        return StringUtils.isNotBlank(value) ? value.trim() : fallback;
    }

    private String stringValue(Object value)
    {
        return value != null ? String.valueOf(value) : "";
    }

    private Map<String, Object> ok(boolean processed, String type)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("received", true);
        result.put("processed", processed);
        result.put("type", type);
        return result;
    }
}
