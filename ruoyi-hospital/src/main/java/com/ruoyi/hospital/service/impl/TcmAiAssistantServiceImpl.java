package com.ruoyi.hospital.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.hospital.service.ITcmAiAssistantService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class TcmAiAssistantServiceImpl implements ITcmAiAssistantService
{
    @Value("${anthropic.api-key:${ANTHROPIC_API_KEY:}}")
    private String anthropicApiKey;

    @Value("${anthropic.model:${ANTHROPIC_MODEL:claude-haiku-4-5-20251001}}")
    private String anthropicModel;

    @Value("${anthropic.endpoint:https://api.anthropic.com/v1/messages}")
    private String anthropicEndpoint;

    @Value("${anthropic.version:2023-06-01}")
    private String anthropicVersion;

    private RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, Object> extractConsultationNotes(Map<String, Object> body)
    {
        String transcript = stringValue(body != null ? body.get("transcript") : null);
        if (StringUtils.isBlank(transcript))
        {
            throw new ServiceException("Transcript is empty");
        }
        if (StringUtils.isBlank(anthropicApiKey))
        {
            throw new ServiceException("AI assistant is not configured. Set ANTHROPIC_API_KEY on the server.");
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", StringUtils.defaultIfBlank(anthropicModel, "claude-haiku-4-5-20251001"));
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0);

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", buildPrompt(body));
        messages.add(message);
        requestBody.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", anthropicApiKey.trim());
        headers.set("anthropic-version", StringUtils.defaultIfBlank(anthropicVersion, "2023-06-01"));

        ResponseEntity<String> response = restTemplate.exchange(
                anthropicEndpoint,
                HttpMethod.POST,
                new HttpEntity<>(requestBody.toJSONString(), headers),
                String.class);
        return parseClaudeResponse(response.getBody());
    }

    private String buildPrompt(Map<String, Object> body)
    {
        JSONObject payload = new JSONObject();
        payload.put("transcript", stringValue(body.get("transcript")));
        payload.put("currentNotes", body.get("currentNotes"));
        payload.put("optionCatalog", body.get("optionCatalog"));
        payload.put("locale", stringValue(body.get("locale")));

        return ""
                + "You extract TCM consultation symptoms from a practitioner-patient conversation.\n"
                + "Return only valid JSON. Do not include markdown, commentary, diagnosis, treatment advice, or final differentiation conclusions.\n"
                + "The conversation may be Chinese, English, or mixed. Use the current notes as prior context and continue editing them without erasing clinician-written content.\n"
                + "Task rules:\n"
                + "1. Record only disease, symptom, observation, history, duration, trigger, progress, tongue, pulse, palpation, bowel, urine, sleep, appetite, thirst, chest, abdomen, gynecology, skin, limb, head, back, muscle, and five-sense-organ information.\n"
                + "2. Discard unrelated chat.\n"
                + "3. For selectable diff fields, choose every matching option using exact strings from optionCatalog.diff[field].\n"
                + "4. If a symptom does not exactly match an option, put concise factual text into the most relevant text field.\n"
                + "5. Put practitioner observations and exterior/body-surface symptoms into diff.otherExterior, including limbs, five sense organs, head, back, muscles, skin, and visible findings.\n"
                + "6. Do not fill diff.conclusions and do not make a final diagnosis.\n"
                + "7. Audio is not provided or stored; work only from the transcript text.\n"
                + "JSON schema:\n"
                + "{\n"
                + "  \"summary\": {\n"
                + "    \"chiefComplaint\": \"\",\n"
                + "    \"chiefComplaintDuration\": \"\",\n"
                + "    \"chiefComplaintDescription\": \"\",\n"
                + "    \"progressOfDisease\": \"\",\n"
                + "    \"summary\": \"\"\n"
                + "  },\n"
                + "  \"diff\": {\n"
                + "    \"coldHeat\": [], \"sweat\": [], \"headDiscomfort\": [], \"headPosition\": [], \"eye\": [], \"ear\": [], \"nose\": [], \"mouth\": [], \"taste\": [],\n"
                + "    \"bodyDiscomforts\": [], \"bodyDiscomfortsLocation\": [], \"skinIssues\": [], \"otherExterior\": \"\",\n"
                + "    \"chest\": [], \"hypochondriac\": [], \"sleep\": [], \"anxietyStress\": null, \"otherChest\": \"\",\n"
                + "    \"appetite\": [], \"thirst\": [], \"abdomen\": [], \"otherAbdomen\": \"\",\n"
                + "    \"bowelMovement\": [], \"urine\": [], \"otherLowerAbdomen\": \"\",\n"
                + "    \"periodCircle\": null, \"periodDuration\": null, \"bloodQuality\": [], \"pms\": [], \"otherFemale\": \"\",\n"
                + "    \"pulse\": [], \"pulseRightHand\": [], \"pulseLeftHand\": [], \"pulseBothCun\": [], \"pulseBothGuan\": [], \"pulseBothChi\": [], \"detailedPulse\": \"\",\n"
                + "    \"tongueColor\": [], \"tongueBody\": [], \"tongueCoating\": [], \"otherTongue\": \"\",\n"
                + "    \"pathologicalChannel\": [], \"pathologicalChanges\": \"\"\n"
                + "  },\n"
                + "  \"evidence\": [],\n"
                + "  \"discarded\": []\n"
                + "}\n"
                + "Input payload:\n"
                + payload.toJSONString();
    }

    private Map<String, Object> parseClaudeResponse(String responseBody)
    {
        if (StringUtils.isBlank(responseBody))
        {
            throw new ServiceException("AI assistant returned an empty response");
        }
        JSONObject response = JSON.parseObject(responseBody);
        String text = extractText(response);
        JSONObject result = JSON.parseObject(extractJsonObject(text));
        if (!result.containsKey("summary") || result.get("summary") == null)
        {
            result.put("summary", new JSONObject());
        }
        if (!result.containsKey("diff") || result.get("diff") == null)
        {
            result.put("diff", new JSONObject());
        }
        if (!result.containsKey("evidence") || result.get("evidence") == null)
        {
            result.put("evidence", new JSONArray());
        }

        Map<String, Object> parsed = new LinkedHashMap<>(result);
        parsed.put("provider", "anthropic");
        parsed.put("model", response.getString("model"));
        parsed.put("usage", response.get("usage"));
        return parsed;
    }

    private String extractText(JSONObject response)
    {
        JSONArray content = response.getJSONArray("content");
        if (content == null || content.isEmpty())
        {
            throw new ServiceException("AI assistant returned no text content");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < content.size(); i++)
        {
            JSONObject block = content.getJSONObject(i);
            String text = block != null ? block.getString("text") : "";
            if (StringUtils.isNotBlank(text))
            {
                builder.append(text).append('\n');
            }
        }
        String text = builder.toString().trim();
        if (StringUtils.isBlank(text))
        {
            throw new ServiceException("AI assistant returned blank text content");
        }
        return text;
    }

    private String extractJsonObject(String text)
    {
        String trimmed = text != null ? text.trim() : "";
        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first < 0 || last <= first)
        {
            throw new ServiceException("AI assistant response was not JSON");
        }
        return trimmed.substring(first, last + 1);
    }

    private String stringValue(Object value)
    {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

