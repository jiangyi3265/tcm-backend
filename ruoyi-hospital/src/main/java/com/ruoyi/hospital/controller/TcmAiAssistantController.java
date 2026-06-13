package com.ruoyi.hospital.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.hospital.service.ITcmAiAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class TcmAiAssistantController
{
    private static final Logger log = LoggerFactory.getLogger(TcmAiAssistantController.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ITcmAiAssistantService aiAssistantService;

    @Value("${deepseek.api-key:${DEEPSEEK_API_KEY:}}")
    private String deepseekApiKey;

    @Value("${deepseek.model:${DEEPSEEK_MODEL:deepseek-v4-flash}}")
    private String deepseekModel;

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @GetMapping("/status")
    public Map<String, Object> status()
    {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("provider", "deepseek");
        status.put("model", StringUtils.defaultIfBlank(deepseekModel, "deepseek-v4-flash"));
        status.put("configured", StringUtils.isNotBlank(deepseekApiKey));
        status.put("version", "deepseek-chat-completions");
        return status;
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @PostMapping("/consultation-notes")
    public ResponseEntity<String> consultationNotes(@RequestBody Map<String, Object> body)
    {
        try
        {
            return jsonResponse(HttpStatus.OK, aiAssistantService.extractConsultationNotes(body));
        }
        catch (ServiceException e)
        {
            return jsonResponse(HttpStatus.BAD_REQUEST, errorBody(e.getMessage()));
        }
        catch (Throwable e)
        {
            log.error("AI assistant request failed", e);
            return jsonResponse(HttpStatus.INTERNAL_SERVER_ERROR, errorBody("AI assistant failed: " + safeMessage(e)));
        }
    }

    private ResponseEntity<String> jsonResponse(HttpStatus status, Map<String, Object> body)
    {
        try
        {
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body));
        }
        catch (JsonProcessingException e)
        {
            log.error("AI assistant response serialization failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"message\":\"AI assistant response serialization failed\"}");
        }
    }

    private Map<String, Object> errorBody(String message)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return body;
    }

    private String safeMessage(Throwable e)
    {
        String message = e.getMessage();
        return message != null && !message.trim().isEmpty() ? message : e.getClass().getSimpleName();
    }
}
