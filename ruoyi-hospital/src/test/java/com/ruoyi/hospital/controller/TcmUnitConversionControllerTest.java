package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmUnitConversion;
import com.ruoyi.hospital.service.ITcmUnitConversionService;

@ExtendWith(MockitoExtension.class)
class TcmUnitConversionControllerTest
{
    @Mock
    private ITcmUnitConversionService conversionService;

    private TcmUnitConversionController controller;

    @BeforeEach
    void setUp()
    {
        controller = new TcmUnitConversionController();
        ReflectionTestUtils.setField(controller, "conversionService", conversionService);
    }

    @Test
    void update_shouldReturnPersistedRecordWhenPayloadIsPartial()
    {
        TcmUnitConversion stored = new TcmUnitConversion();
        stored.setId(9L);
        stored.setFromUnit("g");
        stored.setToUnit("kg");
        stored.setFactor(new BigDecimal("1.5"));
        stored.setNotes("smoke-updated");

        when(conversionService.updateTcmUnitConversion(any())).thenReturn(1);
        when(conversionService.selectTcmUnitConversionById(9L)).thenReturn(stored);

        Map<String, Object> body = new HashMap<>();
        body.put("factor", "1.5");
        body.put("notes", "smoke-updated");

        Map<String, Object> response = controller.update(9L, body);

        assertEquals("g", response.get("fromUnit"));
        assertEquals("kg", response.get("toUnit"));
        assertEquals(new BigDecimal("1.5"), response.get("factor"));
        assertEquals("smoke-updated", response.get("notes"));
    }

    @Test
    void update_shouldThrowWhenConversionMissingAfterUpdate()
    {
        when(conversionService.updateTcmUnitConversion(any())).thenReturn(0);
        when(conversionService.selectTcmUnitConversionById(9L)).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("factor", "1.5");

        ServiceException ex = assertThrows(ServiceException.class, () -> controller.update(9L, body));

        assertEquals("单位换算不存在", ex.getMessage());
    }
}
