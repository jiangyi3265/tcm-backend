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
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
