package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.hospital.domain.TcmTreatmentTemplate;
import com.ruoyi.hospital.mapper.TcmTreatmentTemplateMapper;

@ExtendWith(MockitoExtension.class)
class TcmTreatmentTemplateServiceImplTest
{
    @Mock
    private TcmTreatmentTemplateMapper templateMapper;

    private TcmTreatmentTemplateServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmTreatmentTemplateServiceImpl();
        ReflectionTestUtils.setField(service, "templateMapper", templateMapper);
    }

    @Test
    void selectList_shouldBackfillMissingDefaultTreatmentTemplates()
    {
        when(templateMapper.selectTcmTreatmentTemplateList(any(TcmTreatmentTemplate.class)))
                .thenReturn(Collections.emptyList());
        when(templateMapper.selectTcmTreatmentTemplateById(anyString())).thenReturn(null);

        List<TcmTreatmentTemplate> result = service.selectTcmTreatmentTemplateList(new TcmTreatmentTemplate());

        assertTrue(result.isEmpty());
        ArgumentCaptor<TcmTreatmentTemplate> captor = ArgumentCaptor.forClass(TcmTreatmentTemplate.class);
        verify(templateMapper, times(10)).insertTcmTreatmentTemplate(captor.capture());
        Set<String> ids = captor.getAllValues().stream().map(TcmTreatmentTemplate::getId).collect(Collectors.toSet());
        assertTrue(ids.contains("tmpl-01"));
        assertTrue(ids.contains("tmpl-10"));
        for (TcmTreatmentTemplate template : captor.getAllValues())
        {
            assertEquals(Integer.valueOf(1), template.getIsActive());
            assertNotNull(template.getCreateTime());
            assertFalse(template.getAcupointsJson().isEmpty());
        }
    }

    @Test
    void selectList_shouldNotOverwriteExistingOrSoftDeletedDefaultTemplates()
    {
        TcmTreatmentTemplate existing = new TcmTreatmentTemplate();
        existing.setId("existing-default");
        when(templateMapper.selectTcmTreatmentTemplateList(any(TcmTreatmentTemplate.class)))
                .thenReturn(Collections.emptyList());
        when(templateMapper.selectTcmTreatmentTemplateById(anyString())).thenReturn(existing);

        service.selectTcmTreatmentTemplateList(new TcmTreatmentTemplate());

        verify(templateMapper, never()).insertTcmTreatmentTemplate(any(TcmTreatmentTemplate.class));
    }

    @Test
    void selectList_shouldAvoidDuplicatingActiveTemplatesWithSameName()
    {
        TcmTreatmentTemplate customInsomnia = new TcmTreatmentTemplate();
        customInsomnia.setId("custom-1");
        customInsomnia.setName("失眠标准方案");
        when(templateMapper.selectTcmTreatmentTemplateList(any(TcmTreatmentTemplate.class)))
                .thenReturn(Collections.singletonList(customInsomnia));
        when(templateMapper.selectTcmTreatmentTemplateById(anyString())).thenReturn(null);

        service.selectTcmTreatmentTemplateList(new TcmTreatmentTemplate());

        ArgumentCaptor<TcmTreatmentTemplate> captor = ArgumentCaptor.forClass(TcmTreatmentTemplate.class);
        verify(templateMapper, times(9)).insertTcmTreatmentTemplate(captor.capture());
        Set<String> insertedNames = new HashSet<>();
        for (TcmTreatmentTemplate template : captor.getAllValues())
        {
            insertedNames.add(template.getName());
        }
        assertFalse(insertedNames.contains("失眠标准方案"));
    }
}
