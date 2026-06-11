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

    @Test
    void getBundle_shouldNormalizeEmailTemplates()
    {
        when(settingMapper.selectAllSettings()).thenReturn(Arrays.asList(
                setting("emailTemplates",
                        "{\"0\":{\"subject\":\"\",\"body\":\"\"},"
                                + "\"invoice\":{\"subject\":\"Paid {{patientName}}\",\"body\":\"\"},"
                                + "\"report\":{\"body\":\"Report {{patientName}}\"}}")));

        Map<String, Object> bundle = service.getBundle();

        assertTrue(bundle.get("emailTemplates") instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> templates = (Map<String, Object>) bundle.get("emailTemplates");
        assertTrue(!templates.containsKey("0"));
        assertTrue(templates.containsKey("appointmentConfirmation"));

        @SuppressWarnings("unchecked")
        Map<String, Object> invoice = (Map<String, Object>) templates.get("invoice");
        assertEquals("Paid {{patientName}}", invoice.get("subject"));
        assertTrue(String.valueOf(invoice.get("body")).contains("发票"));

        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) templates.get("consultationRecord");
        assertEquals("Report {{patientName}}", report.get("body"));
    }

    @Test
    void getBundle_shouldIncludeAppointmentDateInDefaultInvoiceSubject()
    {
        when(settingMapper.selectAllSettings()).thenReturn(Collections.emptyList());

        Map<String, Object> bundle = service.getBundle();

        @SuppressWarnings("unchecked")
        Map<String, Object> templates = (Map<String, Object>) bundle.get("emailTemplates");
        @SuppressWarnings("unchecked")
        Map<String, Object> invoice = (Map<String, Object>) templates.get("invoice");
        assertTrue(String.valueOf(invoice.get("subject")).contains("{{appointmentDate}}"));
    }

    @Test
    void updateBaseSettings_shouldCleanEmailTemplatesBeforeSaving()
    {
        when(settingMapper.selectSettingByKey("emailTemplates")).thenReturn(null);
        when(settingMapper.selectAllSettings()).thenReturn(Arrays.asList(
                setting("emailTemplates", "{\"invoice\":{\"subject\":\"Paid\",\"body\":\"\"},\"0\":{}}")));

        Map<String, Object> input = new HashMap<>();
        Map<String, Object> templates = new HashMap<>();
        templates.put("0", Collections.emptyMap());
        Map<String, Object> invoice = new HashMap<>();
        invoice.put("subject", "Paid");
        invoice.put("body", "");
        templates.put("invoice", invoice);
        input.put("emailTemplates", templates);

        Map<String, Object> updated = service.updateBaseSettings(input);

        ArgumentCaptor<TcmClinicSetting> captor = ArgumentCaptor.forClass(TcmClinicSetting.class);
        verify(settingMapper).insertSetting(captor.capture());
        assertEquals("emailTemplates", captor.getValue().getSettingKey());
        assertTrue(!captor.getValue().getSettingValue().contains("\"0\""));
        assertTrue(captor.getValue().getSettingValue().contains("appointmentConfirmation"));
        assertTrue(updated.get("emailTemplates") instanceof Map);
    }

    private TcmClinicSetting setting(String key, String value)
    {
        TcmClinicSetting setting = new TcmClinicSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
