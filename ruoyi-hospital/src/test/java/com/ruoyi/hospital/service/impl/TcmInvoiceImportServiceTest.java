package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.mapper.*;
import com.ruoyi.hospital.service.*;

class TcmInvoiceImportServiceTest {
    private final String id = "ad5c8e20-0938-4a6b-9a80-b1e053c4d320";
    private final TcmClinicSettingMapper settings = mock(TcmClinicSettingMapper.class);
    private final TcmInventoryItemMapper inventoryMapper = mock(TcmInventoryItemMapper.class);
    private final ITcmInventoryService inventory = mock(ITcmInventoryService.class);
    private final ITcmHerbDictService herbs = mock(ITcmHerbDictService.class);
    private TcmInvoiceImportService service;
    private TcmClinicSetting preview;

    @BeforeEach void setup() {
        service = new TcmInvoiceImportService();
        ReflectionTestUtils.setField(service, "settingMapper", settings);
        ReflectionTestUtils.setField(service, "inventoryMapper", inventoryMapper);
        ReflectionTestUtils.setField(service, "inventoryService", inventory);
        ReflectionTestUtils.setField(service, "herbService", herbs);
        preview = new TcmClinicSetting(); preview.setSettingKey("inventoryInvoice:" + id);
        preview.setSettingValue(JSONObject.of("createdAt", System.currentTimeMillis()).toJSONString());
        when(settings.selectSettingForUpdate(preview.getSettingKey())).thenReturn(preview);
    }
    @Test void confirmedInvoiceAddsStockAndSetsOriginalCostAndDoubleSellingPrice() {
        TcmHerbDict herb = new TcmHerbDict(); herb.setId("h1"); herb.setName("通草"); herb.setIsActive(1);
        when(herbs.selectTcmHerbDictById("h1")).thenReturn(herb);
        TcmInventoryItem item = new TcmInventoryItem(); item.setId("i1"); item.setHerbDictId("h1");
        item.setCategory("powder"); item.setUnit("bag"); item.setIsActive(1); item.setQuantity(new BigDecimal("8"));
        when(inventoryMapper.selectTcmInventoryItemForUpdate("i1")).thenReturn(item);
        Map<String, Object> body = Map.of("invoiceId", id, "currency", "CAD", "items", List.of(Map.of(
            "inventoryId", "i1", "herbDictId", "h1", "category", "powder", "unit", "bag", "quantity", 3,
            "purchasePrice", 2.5, "gramsPerPacket", 10)));
        service.confirm(body);
        assertEquals(0, new BigDecimal("11").compareTo(item.getQuantity()));
        assertEquals(0, new BigDecimal("5").compareTo(item.getPricePerUnit()));
        assertEquals(0, new BigDecimal("2.5").compareTo(JSONObject.parseObject(item.getPayload()).getBigDecimal("purchasePrice")));
        service.confirm(body);
        verify(inventory, times(1)).updateTcmInventoryItem(item);
        assertEquals(0, new BigDecimal("11").compareTo(item.getQuantity()));
    }
    @Test void mismatchedCurrencyCannotUpdateStock() {
        assertThrows(ServiceException.class, () -> service.confirm(Map.of("invoiceId", id, "currency", "USD", "items", List.of())));
        verifyNoInteractions(inventory, herbs);
    }
    @Test void importedInvoiceIsIdempotent() {
        preview.setSettingValue(JSONObject.of("imported", true).toJSONString());
        assertEquals(true, service.confirm(Map.of("invoiceId", id)).get("alreadyImported"));
        verifyNoInteractions(inventory, herbs, inventoryMapper);
    }
}
