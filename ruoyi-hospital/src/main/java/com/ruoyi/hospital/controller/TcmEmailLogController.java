package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmEmailLog;
import com.ruoyi.hospital.service.ITcmEmailLogService;
import com.ruoyi.hospital.service.ITcmEmailService;
import com.ruoyi.hospital.util.EmailTemplateRegistry;

@RestController
@RequestMapping("/api/email-logs")
public class TcmEmailLogController
{
    @Autowired
    private ITcmEmailLogService emailLogService;

    @Autowired
    private ITcmEmailService emailService;

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("")
    public List<TcmEmailLog> list()
    {
        return emailLogService.selectTcmEmailLogList(new TcmEmailLog());
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,cashier')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        String to = stringValue(body.get("to"));
        if (to == null)
        {
            to = stringValue(body.get("toEmail"));
        }
        String subject = stringValue(body.get("subject"));
        String emailBody = resolveBody(body);
        Map<String, Object> variables = resolveVariables(body);
        if (subject != null && !variables.isEmpty())
        {
            subject = EmailTemplateRegistry.renderText(subject, variables);
        }
        String type = stringValue(body.get("type"));
        if (type == null)
        {
            type = stringValue(body.get("emailType"));
        }
        String templateKey = stringValue(body.get("templateKey"));
        boolean useTemplate = Boolean.TRUE.equals(body.get("useTemplate"))
                || templateKey != null;

        boolean sent = useTemplate
                ? emailService.sendTemplateAndLog(to, templateKey, variables, subject, emailBody, type)
                : emailService.sendAndLog(to, subject, emailBody, type);

        Map<String, Object> result = new HashMap<>();
        result.put("success", sent);
        result.put("to", to);
        result.put("subject", subject);
        result.put("templateKey", templateKey);
        return result;
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,cashier')")
    @PostMapping("/{id}/resend")
    public Map<String, Object> resend(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body)
    {
        TcmEmailLog source = emailLogService.selectTcmEmailLogById(id);
        if (source == null)
        {
            throw new ServiceException("email log not found");
        }
        Map<String, Object> overrides = body != null ? body : new HashMap<>();
        String to = stringValue(overrides.get("to"));
        if (to == null)
        {
            to = stringValue(overrides.get("toEmail"));
        }
        if (to == null)
        {
            to = source.getToEmail();
        }
        String subject = stringValue(overrides.get("subject"));
        if (subject == null)
        {
            subject = source.getSubject();
        }
        String emailBody = resolveBody(overrides);
        if (emailBody == null)
        {
            emailBody = source.getBody();
        }
        String type = stringValue(overrides.get("type"));
        if (type == null)
        {
            type = stringValue(overrides.get("emailType"));
        }
        if (type == null)
        {
            type = source.getEmailType();
        }
        JSONObject payload = parsePayload(source.getPayload());
        String templateKey = stringValue(overrides.get("templateKey"));
        if (templateKey == null)
        {
            templateKey = stringValue(payload.get("templateKey"));
        }
        Map<String, Object> variables = resolveVariables(overrides);
        if (variables.isEmpty() && payload.get("variables") instanceof Map<?, ?>)
        {
            variables = resolveVariables(payload);
        }
        boolean sent = templateKey != null
                ? emailService.sendTemplateAndLog(to, templateKey, variables, subject, emailBody, type)
                : emailService.sendAndLog(to, subject, emailBody, type);
        Map<String, Object> result = new HashMap<>();
        result.put("success", sent);
        result.put("to", to);
        result.put("subject", subject);
        result.put("type", type);
        result.put("templateKey", templateKey);
        result.put("sourceLogId", id);
        return result;
    }

    private String resolveBody(Map<String, Object> body)
    {
        String direct = stringValue(body.get("body"));
        if (direct != null)
        {
            return direct;
        }
        String template = stringValue(body.get("template"));
        if (template == null)
        {
            template = stringValue(body.get("templateBody"));
        }
        if (template == null)
        {
            return null;
        }
        Object variables = body.get("variables");
        if (variables instanceof Map<?, ?>)
        {
            return EmailTemplateRegistry.renderText(template, resolveVariables(body));
        }
        return template;
    }

    private Map<String, Object> resolveVariables(Map<String, Object> body)
    {
        Map<String, Object> variables = new HashMap<>();
        Object source = body.get("variables");
        if (source instanceof Map<?, ?>)
        {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) source).entrySet())
            {
                if (entry.getKey() != null)
                {
                    variables.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return variables;
    }

    private String stringValue(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private JSONObject parsePayload(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return new JSONObject();
        }
        try
        {
            return JSON.parseObject(value);
        }
        catch (Exception ignored)
        {
            return new JSONObject();
        }
    }
}
