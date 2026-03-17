package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
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
        String emailBody = (String) body.get("body");
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
}
