package com.ruoyi.hospital.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmPriceList;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmPriceListMapper;
import com.ruoyi.hospital.mapper.TcmRoomMapper;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;
import com.ruoyi.hospital.service.ITcmSettingsService;
import com.ruoyi.hospital.util.EmailTemplateRegistry;
import com.ruoyi.hospital.utils.PayloadUtils;

/**
 * 诊所设置 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmSettingsServiceImpl implements ITcmSettingsService
{
    @Autowired
    private TcmClinicSettingMapper settingMapper;

    @Autowired
    private TcmRoomMapper roomMapper;

    @Autowired
    private TcmServiceTypeMapper serviceTypeMapper;

    @Autowired
    private TcmPriceListMapper priceListMapper;

    @Value("${stripe.publishable-key:}")
    private String configuredStripePublishableKey;

    @Value("${stripe.secret-key:}")
    private String configuredStripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String configuredStripeWebhookSecret;

    @Value("${stripe.terminal-reader-id:}")
    private String configuredStripeTerminalReaderId;

    private static final String STRIPE_PUBLISHABLE_KEY = "stripePublishableKey";
    private static final String STRIPE_SECRET_KEY = "stripeSecretKey";
    private static final String STRIPE_WEBHOOK_SECRET = "stripeWebhookSecret";
    private static final String STRIPE_TERMINAL_READER_ID = "stripeTerminalReaderId";
    private static final java.util.Set<String> STRIPE_STORAGE_KEYS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    STRIPE_PUBLISHABLE_KEY,
                    STRIPE_SECRET_KEY,
                    STRIPE_WEBHOOK_SECRET,
                    STRIPE_TERMINAL_READER_ID));

    private static final java.util.Set<String> NUMERIC_SETTINGS = new java.util.HashSet<>(
            java.util.Arrays.asList("taxRate", "profitRatio"));
    private static final java.util.Set<String> INT_SETTINGS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "practitionerInterval",
                    "publicBookingAdvanceDays",
                    "publicBookingDripWindowDays",
                    "publicBookingDripMinutes"));
    private static final java.util.Set<String> JSON_SETTINGS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "practitionerIntervals",
                    "patentMedicines",
                    "formulaCategories",
                    "differentiationNames",
                    "emailTemplates",
                    "consentTemplate",
                    "thirdPartySignature",
                    "clinicSeal",
                    "practitionerProfile"));

    /**
     * 获取所有设置项的捆绑包（扁平化格式，与前端 settings store 对齐）
     */
    @Override
    public Map<String, Object> getBundle()
    {
        Map<String, Object> bundle = new LinkedHashMap<>();

        // Flatten clinic settings into top-level keys with proper types
        List<TcmClinicSetting> settingList = settingMapper.selectAllSettings();
        for (TcmClinicSetting setting : settingList)
        {
            String key = setting.getSettingKey();
            if (STRIPE_STORAGE_KEYS.contains(key))
            {
                continue;
            }
            bundle.put(key, parseSettingValue(key, setting.getSettingValue()));
        }
        bundle.put("emailTemplates", EmailTemplateRegistry.normalize(bundle.get("emailTemplates")));
        bundle.put("stripeSettings", getStripeSettings());

        // Rooms: convert isActive Integer to boolean
        List<TcmRoom> rooms = roomMapper.selectTcmRoomList(new TcmRoom());
        List<Map<String, Object>> roomMaps = new ArrayList<>();
        for (TcmRoom r : rooms)
        {
            roomMaps.add(PayloadUtils.flattenRoom(r));
        }
        bundle.put("rooms", roomMaps);

        // ServiceTypes: convert list to Map keyed by serviceKey (frontend expects object not array)
        List<TcmServiceType> serviceTypes = serviceTypeMapper.selectTcmServiceTypeList(new TcmServiceType());
        Map<String, Object> stMap = new LinkedHashMap<>();
        for (TcmServiceType st : serviceTypes)
        {
            stMap.put(st.getServiceKey(), PayloadUtils.flattenServiceType(st));
        }
        bundle.put("serviceTypes", stMap);

        // PriceLists: flatten (isActive to boolean, items JSON to array)
        List<TcmPriceList> priceLists = priceListMapper.selectTcmPriceListList(new TcmPriceList());
        bundle.put("priceLists", PayloadUtils.flattenPriceLists(priceLists));

        return bundle;
    }

    /**
     * 更新基础设置
     */
    @Override
    public Map<String, Object> updateBaseSettings(Map<String, Object> data)
    {
        for (Map.Entry<String, Object> entry : data.entrySet())
        {
            String key = entry.getKey();
            String value = serializeSettingValue(key, entry.getValue());

            TcmClinicSetting existing = settingMapper.selectSettingByKey(key);
            if (existing != null)
            {
                existing.setSettingValue(value);
                settingMapper.updateSetting(existing);
            }
            else
            {
                TcmClinicSetting newSetting = new TcmClinicSetting();
                newSetting.setSettingKey(key);
                newSetting.setSettingValue(value);
                settingMapper.insertSetting(newSetting);
            }
        }

        // Return updated settings with proper types (与 getBundle 保持一致)
        List<TcmClinicSetting> settingList = settingMapper.selectAllSettings();
        Map<String, Object> settingsMap = new HashMap<>();
        for (TcmClinicSetting setting : settingList)
        {
            String key = setting.getSettingKey();
            if (STRIPE_STORAGE_KEYS.contains(key))
            {
                continue;
            }
            settingsMap.put(key, parseSettingValue(key, setting.getSettingValue()));
        }
        settingsMap.put("emailTemplates", EmailTemplateRegistry.normalize(settingsMap.get("emailTemplates")));
        settingsMap.put("stripeSettings", getStripeSettings());
        return settingsMap;
    }

    @Override
    public Map<String, Object> getStripeSettings()
    {
        String publishableKey = getStripePublishableKey();
        String secretKey = getStripeSecretKey();
        String webhookSecret = getStripeWebhookSecret();
        String readerId = getStripeTerminalReaderId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("publishableKey", StringUtils.defaultString(publishableKey));
        result.put("terminalReaderId", StringUtils.defaultString(readerId));
        result.put("secretKeyConfigured", StringUtils.isNotBlank(secretKey));
        result.put("secretKeyMasked", maskSecret(secretKey));
        result.put("webhookSecretConfigured", StringUtils.isNotBlank(webhookSecret));
        result.put("webhookSecretMasked", maskSecret(webhookSecret));
        return result;
    }

    @Override
    public Map<String, Object> updateStripeSettings(Map<String, Object> data)
    {
        if (data == null)
        {
            return getStripeSettings();
        }
        Object publishableKeyValue = firstSettingValue(data, "publishableKey", "stripePublishableKey");
        if (publishableKeyValue != null)
        {
            saveSetting(STRIPE_PUBLISHABLE_KEY, cleanSettingText(publishableKeyValue));
        }
        Object terminalReaderIdValue = firstSettingValue(data, "terminalReaderId", "stripeTerminalReaderId", "readerId");
        if (terminalReaderIdValue != null)
        {
            saveSetting(STRIPE_TERMINAL_READER_ID, cleanSettingText(terminalReaderIdValue));
        }
        Object secretKeyValue = firstSettingValue(data, "secretKey", "stripeSecretKey");
        if (secretKeyValue != null)
        {
            String secretKey = cleanSettingText(secretKeyValue);
            if (StringUtils.isNotBlank(secretKey) && !isMaskedSecretValue(secretKey))
            {
                saveSetting(STRIPE_SECRET_KEY, secretKey);
            }
        }
        Object webhookSecretValue = firstSettingValue(data, "webhookSecret", "stripeWebhookSecret");
        if (webhookSecretValue != null)
        {
            String webhookSecret = cleanSettingText(webhookSecretValue);
            if (StringUtils.isNotBlank(webhookSecret) && !isMaskedSecretValue(webhookSecret))
            {
                saveSetting(STRIPE_WEBHOOK_SECRET, webhookSecret);
            }
        }
        return getStripeSettings();
    }

    @Override
    public String getStripePublishableKey()
    {
        return defaultConfiguredSetting(STRIPE_PUBLISHABLE_KEY, configuredStripePublishableKey);
    }

    @Override
    public String getStripeSecretKey()
    {
        return defaultConfiguredSetting(STRIPE_SECRET_KEY, configuredStripeSecretKey);
    }

    @Override
    public String getStripeWebhookSecret()
    {
        return defaultConfiguredSetting(STRIPE_WEBHOOK_SECRET, configuredStripeWebhookSecret);
    }

    @Override
    public String getStripeTerminalReaderId()
    {
        return defaultConfiguredSetting(STRIPE_TERMINAL_READER_ID, configuredStripeTerminalReaderId);
    }

    private Object parseSettingValue(String key, String value)
    {
        if (value == null)
        {
            return null;
        }
        if ("emailTemplates".equals(key))
        {
            return EmailTemplateRegistry.normalize(value);
        }
        if (NUMERIC_SETTINGS.contains(key))
        {
            try { return Double.parseDouble(value); }
            catch (NumberFormatException e) { return value; }
        }
        if (INT_SETTINGS.contains(key))
        {
            try { return Integer.parseInt(value); }
            catch (NumberFormatException e) { return value; }
        }
        if (JSON_SETTINGS.contains(key))
        {
            try
            {
                Object parsed = JSON.parse(value);
                return parsed != null ? parsed : value;
            }
            catch (Exception e)
            {
                return value;
            }
        }
        return value;
    }

    private String serializeSettingValue(String key, Object value)
    {
        if (value == null)
        {
            return "emailTemplates".equals(key) ? JSON.toJSONString(EmailTemplateRegistry.normalize(value)) : null;
        }
        if ("emailTemplates".equals(key))
        {
            return JSON.toJSONString(EmailTemplateRegistry.normalize(value));
        }
        if (JSON_SETTINGS.contains(key) || value instanceof Map || value instanceof Collection)
        {
            return JSON.toJSONString(value);
        }
        return String.valueOf(value);
    }

    private String defaultConfiguredSetting(String settingKey, String fallback)
    {
        TcmClinicSetting saved = settingMapper.selectSettingByKey(settingKey);
        if (saved != null && StringUtils.isNotBlank(saved.getSettingValue()))
        {
            return saved.getSettingValue().trim();
        }
        return StringUtils.defaultString(fallback).trim();
    }

    private void saveSetting(String key, String value)
    {
        TcmClinicSetting existing = settingMapper.selectSettingByKey(key);
        if (existing != null)
        {
            existing.setSettingValue(value);
            settingMapper.updateSetting(existing);
        }
        else
        {
            TcmClinicSetting newSetting = new TcmClinicSetting();
            newSetting.setSettingKey(key);
            newSetting.setSettingValue(value);
            settingMapper.insertSetting(newSetting);
        }
    }

    private String cleanSettingText(Object value)
    {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Object firstSettingValue(Map<String, Object> data, String... keys)
    {
        if (data == null || keys == null)
        {
            return null;
        }
        for (String key : keys)
        {
            if (data.containsKey(key))
            {
                return data.get(key);
            }
        }
        return null;
    }

    private boolean isMaskedSecretValue(String value)
    {
        String text = cleanSettingText(value);
        return text.contains("...")
                || text.contains("***")
                || text.toLowerCase().contains("masked");
    }

    private String maskSecret(String value)
    {
        String text = cleanSettingText(value);
        if (StringUtils.isBlank(text))
        {
            return "";
        }
        if (text.length() <= 12)
        {
            return "****" + text.substring(Math.max(0, text.length() - 4));
        }
        return text.substring(0, 7) + "..." + text.substring(text.length() - 4);
    }
}
