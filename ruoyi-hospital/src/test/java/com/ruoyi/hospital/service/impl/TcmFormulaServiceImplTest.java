package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmFormula;
import com.ruoyi.hospital.domain.TcmFormulaItem;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.mapper.TcmFormulaItemMapper;
import com.ruoyi.hospital.mapper.TcmFormulaMapper;
import com.ruoyi.hospital.service.ITcmHerbDictService;

@ExtendWith(MockitoExtension.class)
class TcmFormulaServiceImplTest
{
    @Mock
    private TcmFormulaMapper formulaMapper;

    @Mock
    private TcmFormulaItemMapper formulaItemMapper;

    @Mock
    private ITcmHerbDictService herbDictService;

    private TcmFormulaServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmFormulaServiceImpl();
        ReflectionTestUtils.setField(service, "formulaMapper", formulaMapper);
        ReflectionTestUtils.setField(service, "formulaItemMapper", formulaItemMapper);
        ReflectionTestUtils.setField(service, "herbDictService", herbDictService);
    }

    @Test
    void insertTcmFormula_shouldRejectItemWithoutHerbDictId()
    {
        TcmFormula formula = formulaWithItem(formulaItem(null, "临时名"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.insertTcmFormula(formula));

        assertEquals("formula item herbDictId is required", ex.getMessage());
    }

    @Test
    void insertTcmFormula_shouldRewriteHerbNameFromDictionary()
    {
        TcmFormula formula = formulaWithItem(formulaItem("herb-1", "旧党参"));
        when(formulaMapper.insertTcmFormula(any(TcmFormula.class))).thenReturn(1);
        when(herbDictService.selectTcmHerbDictById("herb-1")).thenReturn(activeHerb("herb-1", "党参"));
        when(formulaItemMapper.batchInsert(any(List.class))).thenReturn(1);

        int affected = service.insertTcmFormula(formula);

        assertEquals(1, affected);
        ArgumentCaptor<List<TcmFormulaItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(formulaItemMapper).batchInsert(captor.capture());
        assertEquals("党参", captor.getValue().get(0).getHerbName());
        assertEquals("党参", formula.getItems().get(0).getHerbName());
    }

    @Test
    void updateTcmFormula_shouldThrowWhenFormulaMissing()
    {
        TcmFormula formula = new TcmFormula();
        formula.setId("missing");
        when(formulaMapper.selectTcmFormulaById("missing")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.updateTcmFormula(formula));

        assertEquals("方剂不存在", ex.getMessage());
    }

    @Test
    void updateTcmFormula_shouldPreserveDeletedAtAndIsActiveForSparsePut()
    {
        TcmFormula existing = new TcmFormula();
        existing.setId("formula-2");
        existing.setDeletedAt("2026-02-02 00:00:00");
        existing.setIsActive(0);

        TcmFormula formula = new TcmFormula();
        formula.setId("formula-2");
        formula.setDescription("新说明");

        when(formulaMapper.selectTcmFormulaById("formula-2")).thenReturn(existing);
        when(formulaMapper.updateTcmFormula(any(TcmFormula.class))).thenReturn(1);

        int affected = service.updateTcmFormula(formula);

        assertEquals(1, affected);
        ArgumentCaptor<TcmFormula> captor = ArgumentCaptor.forClass(TcmFormula.class);
        verify(formulaMapper).updateTcmFormula(captor.capture());
        verify(formulaItemMapper, never()).deleteByFormulaId(any(String.class));
        assertEquals("2026-02-02 00:00:00", captor.getValue().getDeletedAt());
        assertEquals(0, captor.getValue().getIsActive());
    }

    private TcmFormula formulaWithItem(TcmFormulaItem item)
    {
        TcmFormula formula = new TcmFormula();
        formula.setId("formula-1");
        formula.setName("测试方");
        formula.setItems(Collections.singletonList(item));
        return formula;
    }

    private TcmFormulaItem formulaItem(String herbDictId, String herbName)
    {
        TcmFormulaItem item = new TcmFormulaItem();
        item.setHerbDictId(herbDictId);
        item.setHerbName(herbName);
        item.setDosage(new BigDecimal("10"));
        item.setUnit("g");
        return item;
    }

    private TcmHerbDict activeHerb(String id, String name)
    {
        TcmHerbDict herb = new TcmHerbDict();
        herb.setId(id);
        herb.setName(name);
        herb.setIsActive(1);
        herb.setDeletedAt(null);
        return herb;
    }
}
