package com.ruoyi.hospital.controller;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.service.ITcmAiAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TcmAiAssistantControllerTest
{
    @Mock
    private ITcmAiAssistantService aiAssistantService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp()
    {
        TcmAiAssistantController controller = new TcmAiAssistantController();
        ReflectionTestUtils.setField(controller, "aiAssistantService", aiAssistantService);
        ReflectionTestUtils.setField(controller, "deepseekApiKey", "sk-test");
        ReflectionTestUtils.setField(controller, "deepseekModel", "deepseek-v4-flash");
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void status_shouldReturnConfigurationStateWithoutApiKey() throws Exception
    {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("deepseek"))
                .andExpect(jsonPath("$.model").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.version").value("deepseek-chat-completions"))
                .andExpect(jsonPath("$.deepseekApiKey").doesNotExist());
    }

    @Test
    void consultationNotes_shouldReturnServiceExceptionMessage() throws Exception
    {
        when(aiAssistantService.extractConsultationNotes(anyMap()))
                .thenThrow(new ServiceException("DeepSeek API request failed: network access error"));

        mockMvc.perform(post("/api/ai/consultation-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("transcript", "neck pain"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("DeepSeek API request failed: network access error"));
    }

    @Test
    void consultationNotes_shouldReturnUnexpectedFailureMessage() throws Exception
    {
        when(aiAssistantService.extractConsultationNotes(anyMap()))
                .thenThrow(new IllegalStateException("serialization failed"));

        mockMvc.perform(post("/api/ai/consultation-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("transcript", "neck pain"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("AI assistant failed: serialization failed"));
    }
}
