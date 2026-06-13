package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ruoyi.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class TcmAiAssistantServiceImplTest
{
    private TcmAiAssistantServiceImpl service;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp()
    {
        service = new TcmAiAssistantServiceImpl();
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "deepseekEndpoint", "https://api.deepseek.com/chat/completions");
        ReflectionTestUtils.setField(service, "deepseekModel", "deepseek-v4-flash");
    }

    @Test
    void extractConsultationNotes_shouldRequireApiKey()
    {
        ReflectionTestUtils.setField(service, "deepseekApiKey", "");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.extractConsultationNotes(Map.of("transcript", "patient has neck pain")));

        assertEquals("AI assistant is not configured. Set DEEPSEEK_API_KEY on the server.", error.getMessage());
    }

    @Test
    void extractConsultationNotes_shouldParseDeepSeekJsonText()
    {
        ReflectionTestUtils.setField(service, "deepseekApiKey", "sk-deepseek-test");
        String aiJson = "{"
                + "\\\"summary\\\":{\\\"chiefComplaint\\\":\\\"Neck pain\\\",\\\"chiefComplaintDescription\\\":\\\"Pain radiates to shoulder\\\"},"
                + "\\\"diff\\\":{\\\"bodyDiscomforts\\\":[\\\"Stiffness\\\"],\\\"otherExterior\\\":\\\"Tight upper back muscles\\\"},"
                + "\\\"evidence\\\":[\\\"patient reports neck stiffness\\\"]"
                + "}";
        String response = "{"
                + "\"id\":\"chatcmpl-test\","
                + "\"model\":\"deepseek-v4-flash\","
                + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"" + aiJson + "\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}"
                + "}";
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-deepseek-test"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Map<String, Object> currentNotes = new LinkedHashMap<>();
        currentNotes.put("summary", Map.of("chiefComplaint", ""));
        Map<String, Object> result = service.extractConsultationNotes(Map.of(
                "transcript", "patient reports neck stiffness",
                "currentNotes", currentNotes,
                "optionCatalog", Map.of("diff", Map.of("bodyDiscomforts", new String[] { "Stiffness" }))));

        Map<?, ?> summary = (Map<?, ?>) result.get("summary");
        Map<?, ?> diff = (Map<?, ?>) result.get("diff");
        assertEquals("Neck pain", summary.get("chiefComplaint"));
        assertEquals("Tight upper back muscles", diff.get("otherExterior"));
        assertEquals("deepseek", result.get("provider"));
        assertEquals("deepseek-v4-flash", result.get("model"));
        server.verify();
    }
}
