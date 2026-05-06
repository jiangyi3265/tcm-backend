package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmEmailLog;
import com.ruoyi.hospital.service.ITcmEmailLogService;
import com.ruoyi.hospital.service.ITcmEmailService;

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
        String to = (String) body.get("to");
        String subject = (String) body.get("subject");
        String emailBody = resolveBody(body);
        String type = (String) body.get("type");

        boolean sent = emailService.sendAndLog(
                to != null ? to : (String) body.get("toEmail"),
                subject,
                emailBody,
                type != null ? type : (String) body.get("emailType"));

        Map<String, Object> result = new HashMap<>();
        result.put("success", sent);
        result.put("to", to);
        result.put("subject", subject);
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
        boolean sent = emailService.sendAndLog(to, subject, emailBody, type);
        Map<String, Object> result = new HashMap<>();
        result.put("success", sent);
        result.put("to", to);
        result.put("subject", subject);
        result.put("type", type);
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
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) variables).entrySet())
            {
                if (entry.getKey() != null && entry.getValue() != null)
                {
                    template = template.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
                }
            }
        }
        return template;
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
}
