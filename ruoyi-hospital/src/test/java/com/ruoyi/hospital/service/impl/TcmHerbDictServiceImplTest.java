package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.mapper.TcmHerbDictMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TcmHerbDictServiceImplTest {
    @Test void renamePropagatesToInventoryAndFormulaTemplates() {
        TcmHerbDictMapper mapper = mock(TcmHerbDictMapper.class);
        TcmHerbDictServiceImpl service = new TcmHerbDictServiceImpl();
        ReflectionTestUtils.setField(service, "herbDictMapper", mapper);
        TcmHerbDict old = new TcmHerbDict(); old.setId("h1"); old.setName("旧通草");
        TcmHerbDict update = new TcmHerbDict(); update.setId("h1"); update.setName(" 通草 ");
        when(mapper.selectTcmHerbDictById("h1")).thenReturn(old);
        when(mapper.updateTcmHerbDict(update)).thenReturn(1);
        service.updateTcmHerbDict(update);
        assertEquals("通草", update.getName());
        verify(mapper).updateInventoryHerbName("h1", "旧通草", "通草");
        verify(mapper).updateFormulaHerbName("h1", "旧通草", "通草");
    }
}
