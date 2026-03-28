package com.ruoyi.hospital.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmPriceList;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmPriceListMapper;
import com.ruoyi.hospital.mapper.TcmRoomMapper;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;
import com.ruoyi.hospital.service.ITcmSettingsService;
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

    private static final java.util.Set<String> NUMERIC_SETTINGS = new java.util.HashSet<>(
            java.util.Arrays.asList("taxRate", "profitRatio"));
    private static final java.util.Set<String> INT_SETTINGS = new java.util.HashSet<>(
            java.util.Arrays.asList("practitionerInterval"));
    private static final java.util.Set<String> JSON_SETTINGS = new java.util.HashSet<>(
            java.util.Arrays.asList("practitionerIntervals"));

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
            bundle.put(key, parseSettingValue(key, setting.getSettingValue()));
        }

        // Rooms: convert isActive Integer to boolean
        List<TcmRoom> rooms = roomMapper.selectTcmRoomList(new TcmRoom());
        List<Map<String, Object>> roomMaps = new ArrayList<>();
        for (TcmRoom r : rooms)
        {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", r.getId());
            rm.put("name", r.getName());
            rm.put("branchId", r.getBranchId());
            rm.put("isActive", r.getIsActive() != null && r.getIsActive() == 1);
            roomMaps.add(rm);
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
            settingsMap.put(key, parseSettingValue(key, setting.getSettingValue()));
        }
        return settingsMap;
    }

    private Object parseSettingValue(String key, String value)
    {
        if (value == null)
        {
            return null;
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
            return null;
        }
        if (JSON_SETTINGS.contains(key) || value instanceof Map || value instanceof Collection)
        {
            return JSON.toJSONString(value);
        }
        return String.valueOf(value);
    }
}
