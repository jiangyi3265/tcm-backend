package com.ruoyi.hospital.service.impl;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class TcmAiAssistantServiceImpl implements ITcmAiAssistantService
{
    @Value("${deepseek.api-key:${DEEPSEEK_API_KEY:}}")
    private String deepseekApiKey;

    @Value("${deepseek.model:${DEEPSEEK_MODEL:deepseek-v4-flash}}")
    private String deepseekModel;

    @Value("${deepseek.endpoint:${DEEPSEEK_ENDPOINT:https://api.deepseek.com/chat/completions}}")
    private String deepseekEndpoint;

    private RestTemplate restTemplate = new RestTemplate();

    @Override
    public Map<String, Object> extractConsultationNotes(Map<String, Object> body)
    {
        String transcript = stringValue(body != null ? body.get("transcript") : null);
        if (StringUtils.isBlank(transcript))
        {
            throw new ServiceException("Transcript is empty");
        }
        if (StringUtils.isBlank(deepseekApiKey))
        {
            throw new ServiceException("AI assistant is not configured. Set DEEPSEEK_API_KEY on the server.");
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", StringUtils.defaultIfBlank(deepseekModel, "deepseek-v4-flash"));
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0);
        requestBody.put("stream", false);
        requestBody.put("thinking", jsonObject("type", "disabled"));
        requestBody.put("response_format", jsonObject("type", "json_object"));

        JSONArray messages = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", buildSystemPrompt());
        messages.add(systemMessage);

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", buildUserPrompt(body));
        messages.add(message);
        requestBody.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepseekApiKey.trim());

        try
        {
            ResponseEntity<String> response = restTemplate.exchange(
                    deepseekEndpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody.toJSONString(), headers),
                    String.class);
            return parseDeepSeekResponse(response.getBody());
        }
        catch (RestClientResponseException e)
        {
            throw new ServiceException("DeepSeek API request failed (" + e.getRawStatusCode() + "): " + summarizeErrorBody(e.getResponseBodyAsString()));
        }
        catch (ResourceAccessException e)
        {
            throw new ServiceException("DeepSeek API request failed: " + StringUtils.defaultIfBlank(e.getMessage(), "network access error"));
        }
        catch (RestClientException e)
        {
            throw new ServiceException("DeepSeek API request failed: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
        catch (RuntimeException e)
        {
            throw new ServiceException("AI assistant failed: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private String buildSystemPrompt()
    {
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
                + "}";
    }

    private String buildUserPrompt(Map<String, Object> body)
    {
        JSONObject payload = new JSONObject();
        payload.put("transcript", stringValue(body.get("transcript")));
        payload.put("currentNotes", body.get("currentNotes"));
        payload.put("optionCatalog", body.get("optionCatalog"));
        payload.put("locale", stringValue(body.get("locale")));

        return "Input payload:\n"
                + payload.toJSONString();
    }

    private Map<String, Object> parseDeepSeekResponse(String responseBody)
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

        Map<String, Object> parsed = toPlainMap(result);
        parsed.put("provider", "deepseek");
        parsed.put("model", response.getString("model"));
        parsed.put("usage", toPlainValue(response.get("usage")));
        return parsed;
    }

    private String extractText(JSONObject response)
    {
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty())
        {
            throw new ServiceException("AI assistant returned no text content");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < choices.size(); i++)
        {
            JSONObject choice = choices.getJSONObject(i);
            JSONObject message = choice != null ? choice.getJSONObject("message") : null;
            String text = message != null ? message.getString("content") : "";
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

    private String summarizeErrorBody(String body)
    {
        if (StringUtils.isBlank(body))
        {
            return "empty response";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private JSONObject jsonObject(String key, Object value)
    {
        JSONObject object = new JSONObject();
        object.put(key, value);
        return object;
    }

    private Map<String, Object> toPlainMap(Map<?, ?> source)
    {
        Map<String, Object> plain = new LinkedHashMap<>();
        if (source == null)
        {
            return plain;
        }
        for (Map.Entry<?, ?> entry : source.entrySet())
        {
            plain.put(String.valueOf(entry.getKey()), toPlainValue(entry.getValue()));
        }
        return plain;
    }

    private Object toPlainValue(Object value)
    {
        if (value instanceof Map<?, ?>)
        {
            return toPlainMap((Map<?, ?>) value);
        }
        if (value instanceof Iterable<?>)
        {
            List<Object> plain = new ArrayList<>();
            for (Object item : (Iterable<?>) value)
            {
                plain.add(toPlainValue(item));
            }
            return plain;
        }
        return value;
    }

    private String stringValue(Object value)
    {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
