package com.ruoyi.hospital.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.impl.TcmSmtpSettingsService;

@RestController
@RequestMapping("/api/settings/smtp")
@PreAuthorize("@ss.hasRole('admin')")
public class TcmSmtpSettingsController
{
    @Autowired private TcmSmtpSettingsService smtp;
    @Autowired private ITcmAuditLogService audit;

    @GetMapping
    public Map<String, Object> status() { return smtp.status(); }

    @PutMapping
    public Map<String, Object> update(@RequestBody Map<String, Object> body)
    {
        Map<String, Object> result = smtp.update(body);
        audit.log("settings", "smtp", "SMTP", "UPDATE", String.valueOf(SecurityUtils.getUserId()), "Updated SMTP configuration");
        return result;
    }

    @PostMapping("/test")
    public Map<String, Object> test() { return smtp.testConnection(); }
}
