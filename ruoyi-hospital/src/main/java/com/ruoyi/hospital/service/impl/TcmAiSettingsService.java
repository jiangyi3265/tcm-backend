package com.ruoyi.hospital.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;

@Service
public class TcmAiSettingsService
{
    @Autowired private TcmClinicSettingMapper settingMapper;
    @Value("${deepseek.api-key:${DEEPSEEK_API_KEY:}}") private String runtimeApiKey;
    @Value("${deepseek.model:${DEEPSEEK_MODEL:deepseek-v4-flash}}") private String runtimeModel;

    public String getApiKey() { return setting("deepseekApiKey", runtimeApiKey); }
    public String getModel() { return setting("deepseekModel", StringUtils.defaultIfBlank(runtimeModel, "deepseek-flash")); }

    private String setting(String key, String fallback) {
        TcmClinicSetting setting = settingMapper.selectSettingByKey(key);
        return StringUtils.defaultIfBlank(setting != null ? setting.getSettingValue() : null, StringUtils.defaultString(fallback)).trim();
    }

    public Map<String, Object> status() {
        String key = getApiKey();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", "deepseek");
        result.put("configured", StringUtils.isNotBlank(key));
        result.put("apiKeyMasked", key.length() > 8 ? "••••" + key.substring(key.length() - 4) : "");
        result.put("model", getModel());
        result.put("invoiceModel", "deepseek-flash");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Map<String, Object> body) {
        String key = body.get("apiKey") == null ? "" : String.valueOf(body.get("apiKey")).trim();
        String model = String.valueOf(body.getOrDefault("model", getModel())).trim();
        if (!java.util.Arrays.asList("deepseek-flash", "deepseek-pro", "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner").contains(model)) {
            throw new ServiceException("Unsupported DeepSeek model");
        }
        if (!key.isEmpty()) {
            if (key.length() > 256 || key.contains("••••") || key.matches(".*\\s.*")) throw new ServiceException("Invalid API key");
            save("deepseekApiKey", key);
        }
        save("deepseekModel", model);
        return status();
    }

    private void save(String key, String value) {
        TcmClinicSetting setting = new TcmClinicSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        settingMapper.insertSetting(setting);
    }
}
