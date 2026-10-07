package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;

class TcmAiSettingsServiceTest {
    @Test void blankKeyPreservesSecretAndResponsesOnlyContainAMask() {
        TcmClinicSettingMapper mapper = mock(TcmClinicSettingMapper.class);
        Map<String, TcmClinicSetting> saved = new HashMap<>();
        when(mapper.selectSettingByKey(anyString())).thenAnswer(call -> saved.get(call.getArgument(0)));
        when(mapper.insertSetting(any())).thenAnswer(call -> { TcmClinicSetting setting = call.getArgument(0); saved.put(setting.getSettingKey(), setting); return 1; });
        TcmAiSettingsService service = new TcmAiSettingsService();
        ReflectionTestUtils.setField(service, "settingMapper", mapper);
        service.update(Map.of("apiKey", "test-secret-12345678", "model", "deepseek-flash"));
        Map<String, Object> result = service.update(Map.of("apiKey", "", "model", "deepseek-pro"));
        assertEquals("test-secret-12345678", service.getApiKey());
        assertEquals("••••5678", result.get("apiKeyMasked"));
        assertFalse(result.containsKey("apiKey"));
        assertEquals("deepseek-pro", service.getModel());
    }
}
