package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.domain.TcmInventoryItem;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmInventoryItemMapper;
import com.ruoyi.hospital.service.ITcmHerbDictService;

@ExtendWith(MockitoExtension.class)
class TcmInventoryServiceImplTest
{
    @Mock
    private TcmInventoryItemMapper inventoryMapper;

    @Mock
    private ITcmHerbDictService herbDictService;

    @Mock
    private TcmConsultationMapper consultationMapper;

    private TcmInventoryServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmInventoryServiceImpl();
        ReflectionTestUtils.setField(service, "inventoryMapper", inventoryMapper);
        ReflectionTestUtils.setField(service, "herbDictService", herbDictService);
        ReflectionTestUtils.setField(service, "consultationMapper", consultationMapper);
    }

    @Test
    void updateTcmInventoryItem_shouldThrowWhenItemMissing()
    {
        TcmInventoryItem item = new TcmInventoryItem();
        item.setId("missing");
        when(inventoryMapper.selectTcmInventoryItemById("missing")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.updateTcmInventoryItem(item));

        assertEquals("inventory item not found", ex.getMessage());
    }

    @Test
    void updateTcmInventoryItem_shouldUpdateWhenItemExists()
    {
        TcmInventoryItem item = new TcmInventoryItem();
        item.setId("exists");
        when(inventoryMapper.selectTcmInventoryItemById("exists")).thenReturn(new TcmInventoryItem());
        when(inventoryMapper.updateTcmInventoryItem(item)).thenReturn(1);

        int affected = service.updateTcmInventoryItem(item);

        assertEquals(1, affected);
        verify(inventoryMapper).updateTcmInventoryItem(item);
    }

    @Test
    void insertTcmInventoryItem_shouldRejectRawHerbWithoutHerbDictId()
    {
        TcmInventoryItem item = new TcmInventoryItem();
        item.setCategory("raw_herbs");
        item.setName("黄芪");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.insertTcmInventoryItem(item));

        assertEquals("raw herb herbDictId is required", ex.getMessage());
    }

    @Test
    void insertTcmInventoryItem_shouldRewriteRawHerbNameFromDictionary()
    {
        TcmInventoryItem item = new TcmInventoryItem();
        item.setCategory("raw_herbs");
        item.setName("旧黄芪");
        item.setHerbDictId("herb-1");

        when(herbDictService.selectTcmHerbDictById("herb-1")).thenReturn(activeHerb("herb-1", "黄芪"));
        when(inventoryMapper.insertTcmInventoryItem(any(TcmInventoryItem.class))).thenReturn(1);

        int affected = service.insertTcmInventoryItem(item);

        assertEquals(1, affected);
        ArgumentCaptor<TcmInventoryItem> captor = ArgumentCaptor.forClass(TcmInventoryItem.class);
        verify(inventoryMapper).insertTcmInventoryItem(captor.capture());
        assertEquals("黄芪", captor.getValue().getName());
        assertEquals("黄芪", item.getName());
    }

    @Test
    void insertTcmInventoryItem_shouldNormalizePillCategoryAliases()
    {
        TcmInventoryItem item = new TcmInventoryItem();
        item.setCategory("Pill");
        item.setName("Liu Wei Di Huang Wan");

        when(inventoryMapper.insertTcmInventoryItem(any(TcmInventoryItem.class))).thenReturn(1);

        int affected = service.insertTcmInventoryItem(item);

        assertEquals(1, affected);
        ArgumentCaptor<TcmInventoryItem> captor = ArgumentCaptor.forClass(TcmInventoryItem.class);
        verify(inventoryMapper).insertTcmInventoryItem(captor.capture());
        assertEquals("pills", captor.getValue().getCategory());
    }

    @Test
    void updateTcmInventoryItem_shouldRewriteRawHerbNameUsingExistingHerbDictId()
    {
        TcmInventoryItem existing = new TcmInventoryItem();
        existing.setId("exists");
        existing.setCategory("raw_herbs");
        existing.setHerbDictId("herb-2");
        existing.setName("党参旧名");

        TcmInventoryItem item = new TcmInventoryItem();
        item.setId("exists");
        item.setName("临时名");

        when(inventoryMapper.selectTcmInventoryItemById("exists")).thenReturn(existing);
        when(herbDictService.selectTcmHerbDictById("herb-2")).thenReturn(activeHerb("herb-2", "党参"));
        when(inventoryMapper.updateTcmInventoryItem(any(TcmInventoryItem.class))).thenReturn(1);

        int affected = service.updateTcmInventoryItem(item);

        assertEquals(1, affected);
        verify(inventoryMapper).updateTcmInventoryItem(item);
        assertEquals("党参", item.getName());
        assertEquals("herb-2", item.getHerbDictId());
    }

    @Test
    void updateTcmInventoryItem_shouldPreserveDeletedAtPayloadAndIsActiveForSparsePut()
    {
        TcmInventoryItem existing = new TcmInventoryItem();
        existing.setId("exists");
        existing.setCategory("raw_herbs");
        existing.setHerbDictId("herb-3");
        existing.setName("白术");
        existing.setDeletedAt("2026-01-01 00:00:00");
        existing.setPayload("{\"locked\":true}");
        existing.setIsActive(0);

        TcmInventoryItem item = new TcmInventoryItem();
        item.setId("exists");
        item.setQuantity(new java.math.BigDecimal("12"));

        when(inventoryMapper.selectTcmInventoryItemById("exists")).thenReturn(existing);
        when(herbDictService.selectTcmHerbDictById("herb-3")).thenReturn(activeHerb("herb-3", "白术"));
        when(inventoryMapper.updateTcmInventoryItem(any(TcmInventoryItem.class))).thenReturn(1);

        service.updateTcmInventoryItem(item);

        ArgumentCaptor<TcmInventoryItem> captor = ArgumentCaptor.forClass(TcmInventoryItem.class);
        verify(inventoryMapper).updateTcmInventoryItem(captor.capture());
        assertEquals("2026-01-01 00:00:00", captor.getValue().getDeletedAt());
        assertEquals("{\"locked\":true}", captor.getValue().getPayload());
        assertEquals(0, captor.getValue().getIsActive());
        assertEquals("raw_herbs", captor.getValue().getCategory());
        assertEquals("herb-3", captor.getValue().getHerbDictId());
        assertEquals("白术", captor.getValue().getName());
    }

    @Test
    void hardDeleteTcmInventoryItem_shouldAllowImmediatelyAfterSoftDelete()
    {
        TcmInventoryItem existing = new TcmInventoryItem();
        existing.setId("inv-1");
        existing.setDeletedAt("2026-04-15 10:00:00");

        when(inventoryMapper.selectTcmInventoryItemById("inv-1")).thenReturn(existing);
        when(inventoryMapper.deleteTcmInventoryItemById("inv-1")).thenReturn(1);

        int affected = service.hardDeleteTcmInventoryItem("inv-1");

        assertEquals(1, affected);
        verify(inventoryMapper).deleteTcmInventoryItemById("inv-1");
    }

    @Test
    void calculateLast30DaysUsage_shouldAggregateReservationAndFallbackItems()
    {
        TcmInventoryItem astragalus = new TcmInventoryItem();
        astragalus.setId("inv-hq");
        astragalus.setName("黄芪");
        astragalus.setCategory("raw_herbs");
        astragalus.setHerbDictId("herb-hq");
        astragalus.setQuantity(new BigDecimal("500"));

        TcmInventoryItem ginsengPowder = new TcmInventoryItem();
        ginsengPowder.setId("inv-rs-p");
        ginsengPowder.setName("人参");
        ginsengPowder.setCategory("powder");
        ginsengPowder.setHerbDictId("herb-rs");
        ginsengPowder.setSupplierId("supplier-p");
        ginsengPowder.setQuantity(new BigDecimal("200"));

        when(consultationMapper.selectTcmConsultationList(any(TcmConsultation.class)))
                .thenReturn(Arrays.asList(
                        consultation("consult-1",
                                "{\"prescriptions\":[{\"id\":\"rx-1\",\"prescriptionType\":\"raw_herbs\",\"quantity\":7,"
                                        + "\"inventoryReservation\":[{\"inventoryId\":\"inv-hq\",\"name\":\"黄芪\",\"reservedQty\":\"35\"}],"
                                        + "\"items\":[{\"name\":\"黄芪\",\"herbDictId\":\"herb-hq\",\"convertedQty\":\"35\"}]}]}"),
                        consultation("consult-2",
                                "{\"prescriptions\":[{\"id\":\"rx-2\",\"prescriptionType\":\"powder\",\"quantity\":7,"
                                        + "\"inventoryReservation\":[],"
                                        + "\"items\":[{\"name\":\"人参\",\"herbDictId\":\"herb-rs\",\"supplierId\":\"supplier-p\",\"convertedQty\":\"14\"}]}]}"),
                        consultation("consult-deleted",
                                "{\"prescriptions\":[{\"id\":\"rx-3\",\"prescriptionType\":\"raw_herbs\",\"deletedAt\":\"2026-04-14 09:00:00\","
                                        + "\"inventoryReservation\":[{\"inventoryId\":\"inv-hq\",\"name\":\"黄芪\",\"reservedQty\":\"99\"}]}]}"),
                        oldConsultation("consult-old",
                                "{\"prescriptions\":[{\"id\":\"rx-4\",\"prescriptionType\":\"raw_herbs\","
                                        + "\"inventoryReservation\":[{\"inventoryId\":\"inv-hq\",\"name\":\"黄芪\",\"reservedQty\":\"88\"}]}]}")));

        Map<String, BigDecimal> usage = service.calculateLast30DaysUsage(Arrays.asList(astragalus, ginsengPowder));

        assertEquals(new BigDecimal("35"), usage.get("inv-hq"));
        assertEquals(new BigDecimal("14"), usage.get("inv-rs-p"));
        assertEquals(2, usage.size());
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

    private TcmConsultation consultation(String id, String payload)
    {
        TcmConsultation consultation = new TcmConsultation();
        consultation.setId(id);
        consultation.setConsultDate("2026-04-10 10:00:00");
        consultation.setPayload(payload);
        return consultation;
    }

    private TcmConsultation oldConsultation(String id, String payload)
    {
        TcmConsultation consultation = new TcmConsultation();
        consultation.setId(id);
        consultation.setConsultDate("2026-02-01 10:00:00");
        consultation.setPayload(payload);
        return consultation;
    }
}
