package com.ruoyi.hospital.controller;

import java.util.Map;

import com.ruoyi.hospital.service.ITcmAiAssistantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class TcmAiAssistantController
{
    @Autowired
    private ITcmAiAssistantService aiAssistantService;

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @PostMapping("/consultation-notes")
    public Map<String, Object> consultationNotes(@RequestBody Map<String, Object> body)
    {
        return aiAssistantService.extractConsultationNotes(body);
    }
}

