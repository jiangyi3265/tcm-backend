package com.ruoyi.hospital.service;

import java.util.Map;

public interface ITcmAiAssistantService
{
    Map<String, Object> extractConsultationNotes(Map<String, Object> body);
}

