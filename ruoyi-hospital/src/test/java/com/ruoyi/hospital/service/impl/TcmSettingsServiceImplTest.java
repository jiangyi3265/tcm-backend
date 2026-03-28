package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmPriceListMapper;
import com.ruoyi.hospital.mapper.TcmRoomMapper;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;

@ExtendWith(MockitoExtension.class)
class TcmSettingsServiceImplTest
{
    @Mock
    private TcmClinicSettingMapper settingMapper;

    @Mock
    private TcmRoomMapper roomMapper;

    @Mock
    private TcmServiceTypeMapper serviceTypeMapper;

    @Mock
    private TcmPriceListMapper priceListMapper;

    private TcmSettingsServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmSettingsServiceImpl();
        ReflectionTestUtils.setField(service, "settingMapper", settingMapper);
        ReflectionTestUtils.setField(service, "roomMapper", roomMapper);
        ReflectionTestUtils.setField(service, "serviceTypeMapper", serviceTypeMapper);
        ReflectionTestUtils.setField(service, "priceListMapper", priceListMapper);

        lenient().when(roomMapper.selectTcmRoomList(any())).thenReturn(Collections.emptyList());
        lenient().when(serviceTypeMapper.selectTcmServiceTypeList(any())).thenReturn(Collections.emptyList());
        lenient().when(priceListMapper.selectTcmPriceListList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void getBundle_shouldParsePractitionerIntervalsJson()
    {
        when(settingMapper.selectAllSettings()).thenReturn(Arrays.asList(
                setting("practitionerInterval", "20"),
                setting("practitionerIntervals", "{\"101\":30,\"102\":45}")));

        Map<String, Object> bundle = service.getBundle();

        assertEquals(20, bundle.get("practitionerInterval"));
        assertTrue(bundle.get("practitionerIntervals") instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> practitionerIntervals = (Map<String, Object>) bundle.get("practitionerIntervals");
        assertEquals(30, practitionerIntervals.get("101"));
        assertEquals(45, practitionerIntervals.get("102"));
    }

    @Test
    void updateBaseSettings_shouldSerializePractitionerIntervalsMap()
    {
        when(settingMapper.selectSettingByKey("practitionerIntervals")).thenReturn(null);
        when(settingMapper.selectAllSettings()).thenReturn(Arrays.asList(
                setting("practitionerIntervals", "{\"201\":25}")));

        Map<String, Object> input = new HashMap<>();
        Map<String, Object> practitionerIntervals = new HashMap<>();
        practitionerIntervals.put("201", 25);
        input.put("practitionerIntervals", practitionerIntervals);

        Map<String, Object> updated = service.updateBaseSettings(input);

        ArgumentCaptor<TcmClinicSetting> captor = ArgumentCaptor.forClass(TcmClinicSetting.class);
        verify(settingMapper).insertSetting(captor.capture());
        assertEquals("practitionerIntervals", captor.getValue().getSettingKey());
        assertEquals("{\"201\":25}", captor.getValue().getSettingValue());
        assertTrue(updated.get("practitionerIntervals") instanceof Map);
    }

    private TcmClinicSetting setting(String key, String value)
    {
        TcmClinicSetting setting = new TcmClinicSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
